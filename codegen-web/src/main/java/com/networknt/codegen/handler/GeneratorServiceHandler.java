package com.networknt.codegen.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jsoniter.any.Any;
import com.networknt.codegen.CodegenWebConfig;
import com.networknt.codegen.FrameworkRegistry;
import com.networknt.codegen.Generator;
import com.networknt.config.Config;
import com.networknt.rpc.Handler;
import com.networknt.rpc.router.ServiceHandler;
import com.networknt.status.Status;
import com.networknt.utility.HashUtil;
import com.networknt.utility.NioUtils;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.form.FormData;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static java.io.File.separator;

/**
 * This is the handler that does the code generation for consumer request. There
 * might multiple generator objects in the request as an array. It loops each
 * generator object to generate projects into the same folder.
 *
 * @author Steve Hu
 */
@ServiceHandler(id="lightapi.net/codegen/generate/0.0.1")
public class GeneratorServiceHandler implements Handler {
    static private final String CONFIG_NAME = "codegen-web";
    static private final String STATUS_INVALID_FRAMEWORK = "ERR11100";
    static private final String STATUS_MISSING_GENERATOR_ITEM = "ERR11101";

    static private final XLogger logger = XLoggerFactory.getXLogger(GeneratorServiceHandler.class);

    static private CodegenWebConfig codegenWebConfig = (CodegenWebConfig) Config.getInstance().getJsonObjectConfig(CONFIG_NAME, CodegenWebConfig.class);


    @Override
    public ByteBuffer handle(HttpServerExchange exchange, Object input)  {
        logger.entry(input);

        // generate a destination folder name.
        String output = HashUtil.generateUUID();
        String zipFile = output + ".zip";
        String projectFolder = codegenWebConfig.getTmpFolder() + separator + output;

        List<Map<String, Object>> generators = getGeneratorListFromFormData((FormData)input);

        if(generators == null || generators.size() == 0) {
            logger.error("Did not receive any generators in the request.");
            Status status = new Status(STATUS_MISSING_GENERATOR_ITEM);
            return NioUtils.toByteBuffer(status.toString());
        }
        for(Map<String, Object> generatorMap: generators) {
            String framework = (String)generatorMap.get("framework");
            Object model = Any.wrap(generatorMap.get("model"));  // should be a JSON of spec or IDL
            Map<String, Object> config; // should be a json of config
            try {
                config = new ObjectMapper().readValue((String)generatorMap.get("config"), Map.class);
            } catch (IOException e) {
                logger.error("Failed to convert the given object to a map representation: " + e.getMessage());
                return null;
            }
            if(!FrameworkRegistry.getInstance().getFrameworks().contains(framework)) {
                Status status = new Status(STATUS_INVALID_FRAMEWORK, framework);
                return NioUtils.toByteBuffer(status.toString());
            }
            // TODO validate the model and config with json schema
            try {
                Generator generator = FrameworkRegistry.getInstance().getGenerator(framework);
                generator.generate(projectFolder, model, Any.wrap(config));
            } catch (Exception e) {
                e.printStackTrace();
                logger.error("Exception:", e);
            }
        }

        try {
            // TODO generated code is in tmp folder, zip and move to the target folder
            NioUtils.create(codegenWebConfig.getZipFolder() + separator + zipFile, projectFolder);
            // delete the project folder.
            Files.walk(Paths.get(projectFolder), FileVisitOption.FOLLOW_LINKS)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .peek(System.out::println)
                    .forEach(File::delete);
            // check if any zip file that needs to be deleted from zipFolder
            NioUtils.deleteOldFiles(codegenWebConfig.getZipFolder(), codegenWebConfig.getZipKeptMinute());
        } catch (Exception e) {
            e.printStackTrace();
            logger.error("Exception:", e);
        }

        // return the zip file
        File file = new File(codegenWebConfig.getZipFolder() + separator + zipFile);
        return NioUtils.toByteBuffer(file);
    }

    /**
     * Will return a list of generator maps, each of which contain a framework, model, and config.
     * This is a bit of a pain since undertow doesn't do the most perfect job in returning a map structured
     * data set when matching a form field's name. But rather 3 distinct arrays. May be normal, not sure.
     * @param formData
     * @return
     */
    private List<Map<String, Object>> getGeneratorListFromFormData(FormData formData) {
        List<Map<String, Object>> generatorsList = new ArrayList<>();

        // Get each array of items.
        Deque<FormData.FormValue> frameworks = formData.get("generator.framework");
        Deque<FormData.FormValue> models = formData.get("generator.model");
        Deque<FormData.FormValue> configs = formData.get("generator.config");

        // If any are smaller or bigger then the others, fail.
        if (frameworks == null || models == null || configs == null) {
            return null;
        } else if (frameworks.size() != models.size() && models.size() != configs.size()) {
            throw new InvalidParameterException("Received un-matching form fields in request: " + frameworks.size() + ":" + models.size() + ":" + configs.size());
        }

        Iterator<FormData.FormValue> frameworksIterator = frameworks.iterator();
        Iterator<FormData.FormValue> modelsIterator = models.iterator();
        Iterator<FormData.FormValue> configsIterator = configs.iterator();

        // Add each generator to the list.
        for (int i = 0; i < frameworks.size(); i++) {
            Map<String, Object> generator = new HashMap<>();
            generator.put("framework", frameworksIterator.next().getValue());
            generator.put("model", modelsIterator.next().getValue());
            generator.put("config", configsIterator.next().getValue());
            generatorsList.add(generator);
        }

        return generatorsList;
    }
}
