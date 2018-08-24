package com.yahoo.container.handler;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.google.inject.Inject;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.LoggingRequestHandler;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class LogHandler extends LoggingRequestHandler {

    private final JsonFactory jsonFactory = new JsonFactory();
    private static final String LOG_DIRECTORY = "/home/y/logs/vespa/";

    @Inject
    public LogHandler(LoggingRequestHandler.Context parentCtx) {
        super(parentCtx);
    }

    @Override
    public HttpResponse handle(HttpRequest request) {
        BufferedReader br;
        final List<File> files;
        try {
            br = new BufferedReader(new FileReader(LOG_DIRECTORY));
            files = Files.walk(Paths.get(LOG_DIRECTORY))
                    .filter(Files::isRegularFile)
                    .map(Path::toFile)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            return new HttpResponse(404) {
                @Override
                public void render(OutputStream outputStream){
                }
        };}
        return new HttpResponse(200) {
            @Override
            public void render(OutputStream outputStream) throws IOException {
                JsonGenerator generator = jsonFactory.createGenerator(outputStream);
                generator.writeStartObject();
                generator.writeArrayFieldStart("entries");
                for(File file : files) {
                    generator.writeObjectField(file.getName(), file);
                }
                String line;
                while ((line = br.readLine()) != null) {
                    outputStream.write(line.getBytes());
                }
            }
        };
    }


}
