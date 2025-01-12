package io.swagger.codegen.languages;

import io.swagger.codegen.*;
import io.swagger.models.properties.ArrayProperty;
import io.swagger.models.properties.BooleanProperty;
import io.swagger.models.properties.DateProperty;
import io.swagger.models.properties.DateTimeProperty;
import io.swagger.models.properties.DecimalProperty;
import io.swagger.models.properties.DoubleProperty;
import io.swagger.models.properties.FloatProperty;
import io.swagger.models.properties.BaseIntegerProperty;
import io.swagger.models.properties.IntegerProperty;
import io.swagger.models.properties.LongProperty;
import io.swagger.models.properties.MapProperty;
import io.swagger.models.properties.Property;
import io.swagger.models.properties.RefProperty;
import io.swagger.models.properties.StringProperty;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.util.*;

/*
* Note: Developed with Unreal 4.24
* Features not yet supported:
* - Default values for all types
* - Enumerations other than String
* - Request formats other than Json (XML)
* - Responses other than Json (Multipart, XML...)
* - In petstore example, Currency is not handled properly
* - OpenAPI 3.0
*/
public class UE4CPPGenerator extends AbstractCppCodegen implements CodegenConfig {
    public static final String CPP_NAMESPACE = "cppNamespace";
    public static final String CPP_NAMESPACE_DESC = "C++ namespace (convention: name::space::for::api).";
    public static final String UNREAL_MODULE_NAME = "unrealModuleName";
    public static final String UNREAL_MODULE_NAME_DESC = "Name of the generated unreal module (optional)";
    public static final String OPTIONAL_PROJECT_FILE_DESC = "Generate Build.cs";
    public static final String SHORT_PATH_AS_OPERATION_ID_FALLBACK = "shortPathAsOperationIdFallback";
    public static final String SHORT_PATH_AS_OPERATION_ID_FALLBACK_DESC = "When opeartion id is not defined, try to figure out a short path and use it as the operation id while avoid naming collision.";

    protected String unrealModuleName = "Swagger";
    // Will be treated as pointer
    protected Set<String> pointerClasses = new HashSet<String>();
    // source folder where to write the files
    protected String privateFolder = "Private";
    protected String publicFolder = "Public";
    // shared module name
    protected String commonModuleName = "SwaggerCommon";
    protected String commonFolder = ".." + File.separator + commonModuleName;
    protected String commonPrivateFolder = commonFolder + File.separator + privateFolder;
    protected String commonPublicFolder = commonFolder + File.separator + publicFolder;
    protected String apiVersion = "1.0.0";
    protected Map<String, String> namespaces = new HashMap<String, String>();
    // Will be included using the <> syntax, not used in Unreal's coding convention
    protected Set<String> systemIncludes = new HashSet<String>();
    protected String cppNamespace = unrealModuleName;
    protected boolean optionalProjectFileFlag = true;
    protected boolean shortPathAsOperationIdFallback = false;
    
    public UE4CPPGenerator() {
        super();

        instantiationTypes.put("map", "TMap");
        instantiationTypes.put("array", "TArray");

        // set the output folder here
        outputFolder = "generated-code/ue4cpp";

        // set modelNamePrefix as default for UE4CPP
        if (modelNamePrefix == "") {
            modelNamePrefix = unrealModuleName;
        }

        /*
         * Models.  You can write model files using the modelTemplateFiles map.
         * if you want to create one template for file, you can do so here.
         * for multiple files for model, just put another entry in the `modelTemplateFiles` with
         * a different extension
         */
        modelTemplateFiles.put(
                "model-header.mustache",
                ".h");

        modelTemplateFiles.put(
                "model-source.mustache",
                ".cpp");

        /*
         * Api classes.  You can write classes for each Api file with the apiTemplateFiles map.
         * as with models, add multiple entries with different extensions for multiple files per
         * class
         */
        apiTemplateFiles.put(
                "api-header.mustache",   // the template to use
                ".h");       // the extension for each file to write

        apiTemplateFiles.put(
                "api-source.mustache",   // the template to use
                ".cpp");       // the extension for each file to write

        apiTemplateFiles.put(
                "api-operations-header.mustache",   // the template to use
                ".h");       // the extension for each file to write

        apiTemplateFiles.put(
                "api-operations-source.mustache",   // the template to use
                ".cpp");       // the extension for each file to write
        
        /*
         * Template Location.  This is the location which templates will be read from.  The generator
         * will use the resource stream to attempt to read the templates.
         */
        embeddedTemplateDir = templateDir = "ue4cpp";

        // CLI options
        addOption(CPP_NAMESPACE, CPP_NAMESPACE_DESC, this.cppNamespace);
        addOption(UNREAL_MODULE_NAME, UNREAL_MODULE_NAME_DESC, this.unrealModuleName);
        addSwitch(CodegenConstants.OPTIONAL_PROJECT_FILE, OPTIONAL_PROJECT_FILE_DESC, this.optionalProjectFileFlag);
        addSwitch(SHORT_PATH_AS_OPERATION_ID_FALLBACK, SHORT_PATH_AS_OPERATION_ID_FALLBACK_DESC, this.shortPathAsOperationIdFallback);

        /*
         * Additional Properties.  These values can be passed to the templates and
         * are available in models, apis, and supporting files
         */
        additionalProperties.put("apiVersion", apiVersion);
        additionalProperties().put("modelNamePrefix", modelNamePrefix);
        additionalProperties().put("modelPackage", modelPackage);
        additionalProperties().put("apiPackage", apiPackage);
        additionalProperties().put("dllapi", unrealModuleName.toUpperCase() + "_API");
        additionalProperties().put("unrealModuleName", unrealModuleName);

        // Write defaults namespace in properties so that it can be accessible in templates.
        // At this point command line has not been parsed so if value is given
        // in command line it will superseed this content
        additionalProperties.put("cppNamespace",cppNamespace);
        additionalProperties.put("unrealModuleName",unrealModuleName);

        /*
         * Language Specific Primitives.  These types will not trigger imports by
         * the client generator
         */
        languageSpecificPrimitives = new HashSet<String>(
                Arrays.asList(
                        "bool",
                        "int32",
                        "int64",
                        "float",
                        "double",
                        "FString",
                        "FDateTime",
                        "FGuid",
                        "TArray",
                        "TArray<uint8>",  // For byte arrays
                        "TMap",
                        "TSharedPtr<FJsonObject>")
        );

        // Put those files into common directory, so that if we have multiple apis generated, we can share code.
        supportingFiles.add(new SupportingFile("model-base-header.mustache", commonPublicFolder, "SwaggerBaseModel.h"));
        supportingFiles.add(new SupportingFile("model-base-source.mustache", commonPrivateFolder, "SwaggerBaseModel.cpp"));
        supportingFiles.add(new SupportingFile("helpers-header.mustache", commonPublicFolder, "SwaggerHelpers.h"));
        supportingFiles.add(new SupportingFile("helpers-source.mustache", commonPrivateFolder, "SwaggerHelpers.cpp"));

        if (optionalProjectFileFlag) {
            supportingFiles.add(new SupportingFile("Build.cs.mustache", unrealModuleName + ".Build.cs"));
            supportingFiles.add(new SupportingFile("module-header.mustache", privateFolder, unrealModuleName + "Module.h"));
            supportingFiles.add(new SupportingFile("module-source.mustache", privateFolder, unrealModuleName + "Module.cpp"));
            supportingFiles.add(new SupportingFile("api-responses-header.mustache", publicFolder, unrealModuleName + "Responses.h"));
            supportingFiles.add(new SupportingFile("api-responses-source.mustache", privateFolder, unrealModuleName + "Responses.cpp"));


            supportingFiles.add(new SupportingFile("common-Build.cs.mustache", commonFolder, commonModuleName + ".Build.cs"));
            supportingFiles.add(new SupportingFile("common-module-header.mustache", commonPrivateFolder, commonModuleName + "Module.h"));
            supportingFiles.add(new SupportingFile("common-module-source.mustache", commonPrivateFolder, commonModuleName + "Module.cpp"));
        }

        super.typeMapping = new HashMap<String, String>();

        // Maps C++ types during call to getSwaggertype, see DefaultCodegen.getSwaggerType and not the types/formats defined in openapi specification
        // "array" is also used explicitly in the generator for containers
        typeMapping.clear();
        typeMapping.put("integer", "int32");
        typeMapping.put("long", "int64");
        typeMapping.put("float", "float");
        typeMapping.put("number", "float"); // BP doesn't support double!
        typeMapping.put("double", "double");
        typeMapping.put("string", "FString");
        typeMapping.put("byte", "uint8");
        typeMapping.put("binary", "TArray<uint8>");
        typeMapping.put("ByteArray", "TArray<uint8>");
        typeMapping.put("password", "FString");
        typeMapping.put("boolean", "bool");
        typeMapping.put("date", "FDateTime");
        typeMapping.put("Date", "FDateTime");
        typeMapping.put("date-time", "FDateTime");
        typeMapping.put("DateTime", "FDateTime");
        typeMapping.put("array", "TArray");
        typeMapping.put("list", "TArray");
        typeMapping.put("map", "TMap");
        typeMapping.put("object", "TSharedPtr<FJsonObject>");
        typeMapping.put("Object", "TSharedPtr<FJsonObject>");
        typeMapping.put("file", "HttpFileInput");
        typeMapping.put("UUID", "FGuid");

        importMapping = new HashMap<String, String>();
        importMapping.put("HttpFileInput", "#include \"" + modelNamePrefix + "Helpers.h\"");

        namespaces = new HashMap<String, String>();
    }

    @Override
    public void processOpts() {
        super.processOpts();

        if (additionalProperties.containsKey("cppNamespace")){
            cppNamespace = (String) additionalProperties.get("cppNamespace");
        }

        additionalProperties.put("cppNamespaceDeclarations", cppNamespace.split("\\::"));

        boolean updateSupportingFiles = false;
        if (additionalProperties.containsKey("unrealModuleName")){
            unrealModuleName = (String) additionalProperties.get("unrealModuleName");
            additionalProperties().put("dllapi", unrealModuleName.toUpperCase() + "_API");
            modelNamePrefix = unrealModuleName;
            updateSupportingFiles = true;
        }

        if(additionalProperties.containsKey("modelNamePrefix")){
            modelNamePrefix = (String) additionalProperties.get("modelNamePrefix");
            updateSupportingFiles = true;
        }

        if (additionalProperties.containsKey(CodegenConstants.OPTIONAL_PROJECT_FILE)) {
            setOptionalProjectFileFlag(convertPropertyToBooleanAndWriteBack(CodegenConstants.OPTIONAL_PROJECT_FILE));
        } else {
            additionalProperties.put(CodegenConstants.OPTIONAL_PROJECT_FILE, optionalProjectFileFlag);
        }

        if (additionalProperties.containsKey(SHORT_PATH_AS_OPERATION_ID_FALLBACK)) {
            this.shortPathAsOperationIdFallback = convertPropertyToBooleanAndWriteBack(SHORT_PATH_AS_OPERATION_ID_FALLBACK);
        }

        if(updateSupportingFiles) {
            supportingFiles.clear();

            supportingFiles.add(new SupportingFile("model-base-header.mustache", commonPublicFolder, "SwaggerBaseModel.h"));
            supportingFiles.add(new SupportingFile("model-base-source.mustache", commonPrivateFolder, "SwaggerBaseModel.cpp"));
            supportingFiles.add(new SupportingFile("helpers-header.mustache", commonPublicFolder, "SwaggerHelpers.h"));
            supportingFiles.add(new SupportingFile("helpers-source.mustache", commonPrivateFolder, "SwaggerHelpers.cpp"));
            if (optionalProjectFileFlag) {
                supportingFiles.add(new SupportingFile("Build.cs.mustache", unrealModuleName + ".Build.cs"));
                supportingFiles.add(new SupportingFile("module-header.mustache", privateFolder, unrealModuleName + "Module.h"));
                supportingFiles.add(new SupportingFile("module-source.mustache", privateFolder, unrealModuleName + "Module.cpp"));
                supportingFiles.add(new SupportingFile("api-responses-header.mustache", publicFolder, unrealModuleName + "Responses.h"));
                supportingFiles.add(new SupportingFile("api-responses-source.mustache", privateFolder, unrealModuleName + "Responses.cpp"));

                supportingFiles.add(new SupportingFile("common-Build.cs.mustache", commonFolder, commonModuleName + ".Build.cs"));
                supportingFiles.add(new SupportingFile("common-module-header.mustache", commonPublicFolder, commonModuleName + "Module.h"));
                supportingFiles.add(new SupportingFile("common-module-source.mustache", commonPrivateFolder, commonModuleName + "Module.cpp"));
            }

            importMapping.put("HttpFileInput", "#include \"" + modelNamePrefix + "Helpers.h\"");
        }
    }

    public void setOptionalProjectFileFlag(boolean flag) {
        this.optionalProjectFileFlag = flag;
    }

    /**
     * Configures the type of generator.
     *
     * @return the CodegenType for this generator
     * @see io.swagger.codegen.CodegenType
     */
    @Override
    public CodegenType getTag() {
        return CodegenType.CLIENT;
    }

    /**
     * Configures a friendly name for the generator.  This will be used by the generator
     * to select the library with the -l flag.
     *
     * @return the friendly name for the generator
     */
    @Override
    public String getName() {
        return "ue4cpp";
    }

    /**
     * Returns human-friendly help for the generator.  Provide the consumer with help
     * tips, parameters here
     *
     * @return A string value for the help message
     */
    @Override
    public String getHelp() {
        return "Generates a Unreal Engine 4 C++ Module.";
    }

    @Override
    public String toModelImport(String name) {
        name = name.substring(1);   // removing "F"

        if (namespaces.containsKey(name)) {
            return "using " + namespaces.get(name) + ";";
        } else if (systemIncludes.contains(name)) {
            return "#include <" + name + ">";
        }

        String folder = modelPackage().replace("::", File.separator);
        if (!folder.isEmpty())
            folder += File.separator;

        return "#include \"" + folder + name + ".h\"";
    }

    @Override
    protected boolean needToImport(String type) {
        boolean shouldImport = super.needToImport(type);
        if(shouldImport)
            return !languageSpecificPrimitives.contains(type);
        else
            return false;
    }

    /**
     * Escapes a reserved word as defined in the `reservedWords` array. Handle escaping
     * those terms here.  This logic is only called if a variable matches the reserved words
     *
     * @return the escaped term
     */
    @Override
    public String escapeReservedWord(String name) {
        if(this.reservedWordsMappings().containsKey(name)) {
            return this.reservedWordsMappings().get(name);
        }
        return "_" + name;
    }

    /**
     * Location to write model files.  You can use the modelPackage() as defined when the class is
     * instantiated
     */
    @Override
    public String modelFileFolder() {
        return outputFolder + File.separator + modelPackage().replace("::", File.separator);
    }

    /**
     * Location to write api files.  You can use the apiPackage() as defined when the class is
     * instantiated
     */
    @Override
    public String apiFileFolder() {
        return outputFolder + File.separator + apiPackage().replace("::", File.separator);
    }

    @Override
    public String modelFilename(String templateName, String tag) {
        String suffix = modelTemplateFiles().get(templateName);
        String folder = privateFolder;
        if(suffix == ".h") {
            folder = publicFolder;
        }

        return modelFileFolder() + File.separator + folder + File.separator + toModelFilename(tag) + suffix;
    }

    @Override
    public String toModelFilename(String name) {
        name = sanitizeName(name);
        return modelNamePrefix + initialCaps(name);
    }

    @Override
    public String apiFilename(String templateName, String tag) {
        String suffix = apiTemplateFiles().get(templateName);
        String folder = privateFolder;
        if(suffix == ".h") {
            folder = publicFolder;
        }

        if ( templateName.startsWith("api-operations") ) {
            return apiFileFolder() + File.separator + folder + File.separator + toApiFilename(tag) + "Operations" + suffix;
        } else {
            return apiFileFolder() + File.separator + folder + File.separator + toApiFilename(tag) + suffix;
        }
    }

    @Override
    public String toApiFilename(String name) {
        name = sanitizeName(name);
        return modelNamePrefix + initialCaps(name) + "Api";
    }

    /**
     * Optional - type declaration.  This is a String which is used by the templates to instantiate your
     * types.  There is typically special handling for different property types
     *
     * @return a string value used as the `dataType` field for model templates, `returnType` for api templates
     */
    @Override
    public String getTypeDeclaration(Property p) {
        String swaggerType = getSwaggerType(p);

        if (p instanceof ArrayProperty) {
            ArrayProperty ap = (ArrayProperty) p;
            Property inner = ap.getItems();
            return getSwaggerType(p) + "<" + getTypeDeclaration(inner) + ">";
        } else if (p instanceof MapProperty) {
            MapProperty mp = (MapProperty) p;
            Property inner = mp.getAdditionalProperties();
            return getSwaggerType(p) + "<FString, " + getTypeDeclaration(inner) + ">";
        }
        if (pointerClasses.contains(swaggerType)) {
            return swaggerType + "*";
        } else if (languageSpecificPrimitives.contains(swaggerType)) {
            return toModelName(swaggerType);
        } else {
            return swaggerType;
        }
    }


    @Override
    public String toDefaultValue(Property p) {
        if (p instanceof StringProperty) {
            StringProperty sp = (StringProperty) p;
            if (sp.getDefault() != null) {
                return "TEXT(\"" + sp.getDefault().toString() + "\")";
            }
            else {
                return null;
            }
        } else if (p instanceof BooleanProperty) {
            BooleanProperty bp = (BooleanProperty) p;
            if (bp.getDefault() != null) {
                return bp.getDefault().toString();
            }
            else {
                return "false";
            }
        } else if (p instanceof DateProperty) {
            return "FDateTime(0)";
        } else if (p instanceof DateTimeProperty) {
            return "FDateTime(0)";
        } else if (p instanceof DoubleProperty) {
            DoubleProperty dp = (DoubleProperty) p;
            if (dp.getDefault() != null) {
                return dp.getDefault().toString();
            }
            else {
                return "0.0";
            }
        } else if (p instanceof FloatProperty) {
            FloatProperty fp = (FloatProperty) p;
            if (fp.getDefault() != null) {
                return fp.getDefault().toString();
            }
            else {
                return "0.0f";
            }
        } else if (p instanceof IntegerProperty) {
            IntegerProperty ip = (IntegerProperty) p;
            if (ip.getDefault() != null) {
                return ip.getDefault().toString();
            }
            else {
                return "0";
            }
        } else if (p instanceof LongProperty) {
            LongProperty lp = (LongProperty) p;
            if (lp.getDefault() != null) {
                return lp.getDefault().toString();
            }
            else {
                return "0";
            }
        } else if (p instanceof BaseIntegerProperty) {
            // catchall for any other format of the swagger specifiction
            // integer type not explicitly handled above
            return "0";
        } else if (p instanceof DecimalProperty) {
            return "0.0";
        } 

        return null;
    }

    /**
     * Optional - swagger type conversion.  This is used to map swagger types in a `Property` into
     * either language specific types via `typeMapping` or into complex models if there is not a mapping.
     *
     * @return a string value of the type or complex model for this property
     * @see io.swagger.models.properties.Property
     */
    @Override
    public String getSwaggerType(Property p) {
        String swaggerType = super.getSwaggerType(p);
        String type = null;
        if (typeMapping.containsKey(swaggerType)) {
            type = typeMapping.get(swaggerType);
            if (languageSpecificPrimitives.contains(type)) {
                return toModelName(type);
            }
            if (pointerClasses.contains(type)) {
                return type;
            }
        } else {
            type = swaggerType;
        }
        return toModelName(type);
    }

    @Override
    public String toModelName(String type) {
        if (typeMapping.keySet().contains(type) ||
                typeMapping.values().contains(type) ||
                importMapping.values().contains(type) ||
                defaultIncludes.contains(type) ||
                languageSpecificPrimitives.contains(type)) {
            return type;
        } else {
            type = sanitizeName(type);
            // We have no choice but adding "F" here. For complex types, like:
            // TMap<FString, ModelType> BorrowedCards;
            // we have no way to add F for ModelType from within the template
            return "F" + modelNamePrefix + Character.toUpperCase(type.charAt(0)) + type.substring(1);
        }
    }

    @Override
    public String toVarName(String name) {
        // sanitize name
        name = sanitizeName(name); // FIXME: a parameter should not be assigned. Also declare the methods parameters as 'final'.

        // if it's all uppper case, convert to lower case
        if (name.matches("^[A-Z_]*$")) {
            name = name.toLowerCase();
        }

        // for reserved word or word starting with number, append _
        if (isReservedWord(name) || name.matches("^\\d.*")) {
            name = escapeReservedWord(name);
        }

        //Unreal variable names are CamelCase
        return camelize(name, false);
    }

    @Override
    public Map<String, Object> postProcessModels(Map<String, Object> objs) {
        // TODO: This could be moved to AbstractCPPGenerator, as model enums are virtually unusable without
        objs = super.postProcessModels(objs);

        // force `required`, because TOptional<> cannot be exposed to BP
        List<Map<String, Object>> models = (List<Map<String, Object>>) objs.get("models");
        for (Map<String, Object> model : models) {
            Object v = model.get("model");
            if (v instanceof CodegenModel) {
                CodegenModel m = (CodegenModel) v;
                List<String> d = new ArrayList<String>();
                for (CodegenProperty p : m.allVars)
                    p.required = true;
            }
        }

        return postProcessModelsEnum(objs);
    }

    @Override
    public Map<String, Object> postProcessOperations(Map<String, Object> objs) {
        // force `required`, because TOptional<> cannot be exposed to BP
        Map<String, Object> operations = (Map<String, Object>) objs.get("operations");
        List<CodegenOperation> operationList = (List<CodegenOperation>) operations.get("operation");

        for (CodegenOperation op1 : operationList) {
            for (CodegenParameter p : op1.allParams) {
                p.required = true;
            }
            for (CodegenParameter p: op1.headerParams) {
                p.required = true;
            }
            for (CodegenResponse r: op1.responses) {
                r.name = r.primitiveType ? modelNamePrefix + sanitizeName(r.dataType) : sanitizeName(r.dataType.substring(1));
            }
        }

        // setup nickname for operations (used as DisplayName)
        for (CodegenOperation op1: operationList) {
            if (StringUtils.isBlank(op1.operationIdOriginal)) {
                op1.nickname = op1.path.replaceAll("\\{", "").replaceAll("\\}", "");
                String[] parts = op1.nickname.split("/");
                op1.nickname = parts[parts.length - 1];
                if (!op1.httpMethod.equalsIgnoreCase("GET")) {  // omit GET because it is most commonly used
                    op1.nickname += op1.httpMethod.toUpperCase();
                }
            }
        }

        // Once I want to make a shorter name for UE4, which ends up solved by DisplayName + HasNativeBreak/Make
        if (shortPathAsOperationIdFallback && operationList.size() > 0)
        {
            String[] fullIds = new String[operationList.size()];
            String[] shortIds = new String[operationList.size()];
            int[] startIndices = new int[operationList.size()];

            HashSet<String> allIds = new HashSet<String>();
            List<String> badIds = new ArrayList<String>();

            for (int i = 0; i < operationList.size(); i ++) {
                CodegenOperation op1 = operationList.get(i);
                if (StringUtils.isBlank(op1.operationIdOriginal)) {
                    fullIds[i] = op1.path.replaceAll("\\{", "").replaceAll("\\}", "").toLowerCase();
                    if (!op1.httpMethod.equalsIgnoreCase("GET")) {  // omit GET because it is most commonly used
                        fullIds[i] = fullIds[i] + op1.httpMethod.toUpperCase();
                    }
                    startIndices[i] = fullIds[i].lastIndexOf("/");
                } else {
                    shortIds[i] = op1.operationId;
                    startIndices[i] = -1;
                }
                shortIds[i] = fullIds[i].substring(startIndices[i] + 1);
            }

            for (String shortId : shortIds) {
                if (allIds.contains(shortId)) {
                    badIds.add(shortId);
                } else {
                    allIds.add(shortId);
                }
            }

            boolean isProgressing = true;
            while (isProgressing && !badIds.isEmpty()) {
                String badId = badIds.remove(badIds.size() - 1);
                System.out.println("processing bad id " + badId);
                if (!allIds.contains(badId)) {
                    continue;
                }

                allIds.remove(badId);

                // check for naming collisions && try to advance
                System.out.println("-----> resolving name collision for " + badId);
                isProgressing = false;
                for (int i = 0; i < operationList.size(); i ++) {
                    if (shortIds[i] != badId) continue;
                    String newId;
                    if (startIndices[i] != -1) {
                        System.out.println("short name for " + operationList.get(i).path + "was " + badId);
                        isProgressing = true;
                        startIndices[i] = fullIds[i].lastIndexOf("/", startIndices[i] - 1);
                        newId = shortIds[i] = fullIds[i].substring(startIndices[i] + 1);
                        System.out.println("extends to " + newId);
                    } else {
                        newId = badId;
                        System.out.println(operationList.get(i).path + "cannot be extend anymore!");
                    }
                    if (allIds.contains(newId)) {
                        badIds.add(shortIds[i]);
                    } else {
                        allIds.add(newId);
                    }
                }
            }

            if (badIds.isEmpty()) {
                for (int i = 0; i < operationList.size(); i ++) {
                    CodegenOperation op1 = operationList.get(i);
                    String[] parts = shortIds[i].split("/");
                    String opId = StringUtils.capitalize(modelNamePrefix);
                    for (String part : parts) {
                        opId += StringUtils.capitalize(part);
                    }
                    op1.operationId = opId;
                    op1.operationIdLowerCase = opId.toLowerCase();
                    op1.operationIdCamelCase = camelize(opId);
                    op1.operationIdSnakeCase = snakeCase(opId);
                }
            }
        }
        return objs;
    }

    // @Override
    // Map<String, Object> postProcessOperationsWithModels(Map<String, Object> objs, List<Object> allModels)
    // {
        // remove unneccessary models
        // if some model has parent, and its type is TMap or TArray, then we remove this model and use TMap/TArray instead
        // if    
    // }

    @Override
    public String toEnumVarName(String name, String datatype) {
        return toVarName(name);
    }

    @Override
    public String toParamName(String name) {
        return toVarName(name);
    }

    @Override
    public String toApiName(String type) {
        return "U" + modelNamePrefix + Character.toUpperCase(type.charAt(0)) + type.substring(1) + "Api";
    }

    @Override
    public String escapeQuotationMark(String input) {
        // remove " to avoid code injection
        return input.replace("\"", "");
    }

    @Override
    public String escapeUnsafeCharacters(String input) {
        return input.replace("*/", "*_/").replace("/*", "/_*");
    }

    @Override
    public String sanitizeName(String name)
    {
        name = super.sanitizeName(name);
        // TODO: This could be moved to AbstractCPPGenerator, C++ does not support "." in symbol names
        name = name.replace(".", "_");
        return name;
    }

    public String toBooleanGetter(String name) {
        return "Is" + getterAndSetterCapitalize(name);
    }

    public String toGetter(String name) {
        return "Get" + getterAndSetterCapitalize(name);
    }

    public String toSetter(String name) {
        return "Set" + getterAndSetterCapitalize(name);
    }

    @Override
    public Map<String, Object> postProcessSupportingFileData(Map<String, Object> objs) {
        Map<String, CodegenResponse> uniqueRsps = new HashMap<String, CodegenResponse>();

        Map<String, Object> apiInfo = (Map<String, Object>) objs.get("apiInfo");
        List<Map<String, Object>> _apis = (List<Map<String, Object>>) apiInfo.get("apis");
        for (Map<String, Object> _api: _apis) {
            Map<String, Object> operations = (Map<String, Object>) _api.get("operations");
            List<CodegenOperation> operationList = (List<CodegenOperation>) operations.get("operation");
            for (CodegenOperation op: operationList) {
                for (CodegenResponse rsp: op.responses) {
                    uniqueRsps.put(rsp.dataType, rsp);
                }
            }
        }

        // cache dataType -> importPath
        Map<String, String> modelImportPaths = new HashMap<String, String>();
        List<Map<String, Object>> models = (List<Map<String, Object>>) objs.get("models");
        for (Map<String, Object> model: models) {
            String importPath = (String) model.get("importPath");
            CodegenModel codegenModel = (CodegenModel) model.get("model");
            String classname = codegenModel.classname;
            modelImportPaths.put(classname, importPath);
        }

        List<String> imports = new ArrayList<String>();

        ArrayList<HashMap<String, String>> rspTypes = new ArrayList<HashMap<String, String>>();
        for (String dataType: uniqueRsps.keySet()) {
            HashMap<String, String> item = new HashMap<String, String>();
            item.put("dataType", dataType);     // dataType for `Content`
            boolean isModel = modelImportPaths.containsKey(dataType);
            String baseName = isModel ? dataType.substring(1) : modelNamePrefix + dataType;    // removing "F"
            item.put("classname", sanitizeName(baseName));  // Response class which holds the Content can use it

            item.put("varName", sanitizeName(dataType));
            item.put("nickname", sanitizeName(dataType));
            if (isModel) {
                item.put("importPath", modelImportPaths.get(dataType));
            }
            rspTypes.add(item);
        }

        objs.put("responseTypes", rspTypes);

        return objs;
    }
}
