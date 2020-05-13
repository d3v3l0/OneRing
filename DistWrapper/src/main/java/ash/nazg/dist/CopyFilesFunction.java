package ash.nazg.dist;

import ash.nazg.config.DataStreamsConfig;
import ash.nazg.config.InvalidConfigValueException;
import org.apache.hadoop.conf.Configurable;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.LocatedFileStatus;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.RemoteIterator;
import org.apache.hadoop.io.compress.*;
import org.apache.parquet.example.data.Group;
import org.apache.parquet.format.converter.ParquetMetadataConverter;
import org.apache.parquet.hadoop.ParquetFileReader;
import org.apache.parquet.hadoop.ParquetReader;
import org.apache.parquet.hadoop.example.GroupReadSupport;
import org.apache.parquet.hadoop.metadata.ParquetMetadata;
import org.apache.parquet.schema.MessageType;
import org.apache.spark.SparkContext;
import org.apache.spark.api.java.function.VoidFunction;
import scala.Tuple3;
import scala.Tuple4;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

public class CopyFilesFunction implements VoidFunction<Tuple4<String, String, String, String>> {
    private static final Map<String, Class<? extends CompressionCodec>> CODECS = new HashMap<>();

    static {
        CODECS.put("gz", GzipCodec.class);
        CODECS.put("gzip", GzipCodec.class);
        CODECS.put("bz2", BZip2Codec.class);
        CODECS.put("snappy", SnappyCodec.class);
        CODECS.put("lz4", Lz4Codec.class);
    }

    private static final int BUFFER_SIZE = 1024 * 1024;

    private final boolean deleteOnSuccess;
    // sink -> schema, columns, delimiter
    private final Map<String, Tuple3<String[], String[], Character>> sinkInfo;

    private String codec;
    private String sink;

    public CopyFilesFunction(boolean deleteOnSuccess, String codec, Map<String, Tuple3<String[], String[], Character>> sinkInfo) {
        this.deleteOnSuccess = deleteOnSuccess;
        this.codec = codec;
        this.sinkInfo = sinkInfo;
    }

    private String getSuffix(String name) {
        if (name != null) {
            String[] parts = name.split("\\.");
            if (parts.length > 1) {
                return parts[parts.length - 1];
            }
        }

        return "";
    }

    private OutputStream decorateOutputStream(Path outputFilePath, Configuration conf) throws Exception {
        FileSystem outputFs = outputFilePath.getFileSystem(conf);
        OutputStream outputStream = outputFs.create(outputFilePath);

        String suffix = getSuffix(outputFilePath.getName()).toLowerCase();
        if (CODECS.containsKey(suffix)) {
            Class<? extends CompressionCodec> cc = CODECS.get(suffix);
            CompressionCodec codec = cc.newInstance();
            ((Configurable) codec).setConf(conf);

            return codec.createOutputStream(outputStream);
        } else {
            return outputStream;
        }
    }

    private InputStream decorateInputStream(Path inputFilePath, Configuration conf) throws Exception {
        String suffix = getSuffix(inputFilePath.getName()).toLowerCase();
        if ("parquet".equalsIgnoreCase(suffix)) {
            ParquetMetadata readFooter = ParquetFileReader.readFooter(conf, inputFilePath, ParquetMetadataConverter.NO_FILTER);
            MessageType schema = readFooter.getFileMetaData().getSchema();

            Tuple3<String[], String[], Character> si = sinkInfo.get(sink);

            String[] sinkColumns = si._2();

            int[] fieldOrder;
            if (sinkColumns != null) {
                fieldOrder = new int[sinkColumns.length];

                for (int i = 0; i < sinkColumns.length; i++) {
                    String column = sinkColumns[i];
                    fieldOrder[i] = schema.getFieldIndex(column);
                }
            } else {
                fieldOrder = IntStream.range(0, schema.getFieldCount()).toArray();
            }

            GroupReadSupport readSupport = new GroupReadSupport();
            readSupport.init(conf, null, schema);
            ParquetReader<Group> reader = ParquetReader.builder(readSupport, inputFilePath).build();

            return new ParquetRecordInputStream(reader, fieldOrder, si._3());
        } else {
            FileSystem inputFs = inputFilePath.getFileSystem(conf);
            InputStream inputStream = inputFs.open(inputFilePath);

            if (CODECS.containsKey(suffix)) {
                Class<? extends CompressionCodec> cc = CODECS.get(suffix);
                CompressionCodec codec = cc.newInstance();
                ((Configurable) codec).setConf(conf);

                inputStream = codec.createInputStream(inputStream);
            }

            if (sink != null) {
                Tuple3<String[], String[], Character> si = sinkInfo.get(sink);

                String[] sinkSchema = si._1();
                String[] sinkColumns = si._2();

                if ((sinkSchema != null) || (sinkColumns != null)) {
                    int[] columnOrder;

                    if (sinkSchema != null) {
                        if (sinkColumns == null) {
                            columnOrder = IntStream.range(0, sinkSchema.length).toArray();
                        } else {
                            Map<String, Integer> schema = new HashMap<>();
                            for (int i = 0; i < sinkSchema.length; i++) {
                                schema.put(sinkSchema[i], i);
                            }

                            Map<Integer, String> columns = new HashMap<>();
                            for (int i = 0; i < sinkColumns.length; i++) {
                                columns.put(i, sinkColumns[i]);
                            }

                            columnOrder = new int[sinkColumns.length];
                            for (int i = 0; i < sinkColumns.length; i++) {
                                columnOrder[i] = schema.get(columns.get(i));
                            }
                        }
                    } else {
                        columnOrder = IntStream.range(0, sinkColumns.length).toArray();
                    }

                    inputStream = new CSVRecordInputStream(inputStream, columnOrder, si._3());
                }
            }

            return inputStream;
        }
    }

    public void mergeAndCopyFiles(List<String> inputFiles, String outputFile, Configuration conf) throws Exception {
        Path outputFilePath = new Path(outputFile);

        try (OutputStream outputStream = decorateOutputStream(outputFilePath, conf)) {
            for (String inputFile : inputFiles) {
                Path inputFilePath = new Path(inputFile);

                try (InputStream inputStream = decorateInputStream(inputFilePath, conf)) {
                    int len;
                    for (byte[] buffer = new byte[BUFFER_SIZE]; (len = inputStream.read(buffer)) > 0; ) {
                        outputStream.write(buffer, 0, len);
                    }
                } catch (Exception e) {
                    FileSystem outFs = outputFilePath.getFileSystem(conf);
                    outFs.delete(outputFilePath, true);

                    throw e;
                }
            }
        }
    }

    @Override
    public void call(Tuple4<String, String, String, String> srcDestGroup) {
        final String src = srcDestGroup._1();
        String dest = srcDestGroup._2();
        final String groupBy = srcDestGroup._3();
        sink = srcDestGroup._4();
        try {
            Path srcPath = new Path(src);

            Configuration conf = SparkContext.getOrCreate().hadoopConfiguration();

            FileSystem srcFS = srcPath.getFileSystem(conf);
            RemoteIterator<LocatedFileStatus> srcFiles = srcFS.listFiles(srcPath, true);

            Pattern pattern = Pattern.compile(groupBy);

            List<String> ret = new ArrayList<>();
            HashSet<String> codecs = new HashSet<>();
            while (srcFiles.hasNext()) {
                String srcFile = srcFiles.next().getPath().toString();

                Matcher m = pattern.matcher(srcFile);
                if (m.matches()) {
                    ret.add(srcFile);
                }

                codecs.add(getSuffix(srcFile));
            }

            if (codecs.isEmpty()) {
                throw new InvalidConfigValueException("No files to copy");
            }

            if ("keep".equalsIgnoreCase(codec)) {
                if ((codecs.size() > 1)) {
                    codec = "none";
                } else {
                    codec = codecs.toArray(new String[0])[0];
                }
            }

            if (CODECS.containsKey(codec)) {
                dest += "." + codec;
            }

            mergeAndCopyFiles(ret, dest, conf);

            if (deleteOnSuccess) {
                srcFS.delete(srcPath, true);
            }
        } catch (Exception e) {
            System.err.println(e.getMessage());
            System.exit(14);
        }
    }
}
