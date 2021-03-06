/**
 * Copyright (C) 2020 Locomizer team and Contributors
 * This project uses New BSD license with do no evil clause. For full text, check the LICENSE file in the root directory.
 */
package ash.nazg.config.tdl;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.hibernate.validator.constraints.NotEmpty;

import javax.validation.Valid;
import java.util.Properties;

public class TaskDefinitionLanguage {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Task {
        @JsonProperty(required = true, value = "op")
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        @Valid
        public Operation[] operations;

        @JsonProperty(required = true, value = "ds")
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        @Valid
        public DataStream[] dataStreams;

        @JsonProperty(required = true, value = "tee")
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        public String[] tees;

        @JsonProperty(required = true, value = "sink")
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        public String[] sink;

        @JsonProperty(value = "prefix")
        public String prefix;

        @JsonProperty(value = "distcp")
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        public Properties distcp;

        @JsonProperty(value = "input")
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        public Properties input;

        @JsonProperty(value = "output")
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        public Properties output;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Operation {
        @JsonProperty(value = "definitions")
        @Valid
        public Definition[] definitions;

        @JsonProperty(value = "dynamicDefs")
        @Valid
        public DynamicDef[] dynamicDefs;

        @JsonProperty(value = "inputs")
        @Valid
        public OpStreams inputs;

        @JsonProperty(value = "outputs")
        @Valid
        public OpStreams outputs;

        @JsonProperty(required = true, value = "name")
        @NotEmpty
        public String name;

        @JsonProperty(required = true, value = "verb")
        @NotEmpty
        public String verb;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class OpStreams {
        @JsonProperty(value = "named")
        @Valid
        public NamedStream[] named;

        @JsonProperty(value = "positional")
        public String[] positionalNames;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class NamedStream {
        @JsonProperty(required = true, value = "name")
        @NotEmpty
        public String name;

        @JsonProperty(required = true, value = "value")
        @NotEmpty
        public String value;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Definition {
        @JsonProperty(required = true, value = "name")
        @NotEmpty
        public String name;

        @JsonProperty(value = "value")
        public String value;

        @JsonProperty(value = "useDefaults")
        public Boolean useDefaults;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DynamicDef {
        @JsonProperty(required = true, value = "name")
        @NotEmpty
        public String name;

        @JsonProperty(value = "value")
        public String value;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DataStream {
        @JsonProperty(required = true, value = "name")
        @NotEmpty
        public String name;

        @JsonProperty(value = "input")
        @Valid
        public StreamDesc input;

        @JsonProperty(value = "output")
        @Valid
        public StreamDesc output;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class StreamDesc {
        @JsonProperty(value = "columns")
        public String[] columns;

        @JsonProperty(value = "delimiter")
        public String delimiter;

        @JsonProperty(value = "partCount")
        public String partCount;

        @JsonProperty(value = "path")
        public String path;
    }
}
