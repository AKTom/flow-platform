/*
 * Copyright 2017 flow.ci
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.flow.platform.plugin.util;

import com.flow.platform.plugin.exception.PluginException;
import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Path;

/**
 * @author yh@firim
 */
public class FileUtil {

    synchronized public static <T> void write(T object, Path path) {
        try (FileWriter writer = new FileWriter(path.toString())) {
            String json = new Gson().toJson(object);
            writer.write(json);
        } catch (IOException e) {
            throw new PluginException("IOException: " + e.getMessage());
        }
    }

    synchronized public static <T> T read(Type clazz, Path path) {
        if (!path.toFile().exists()) {
            return null;
        }

        try (FileReader fileReader = new FileReader(path.toString())) {
            try (JsonReader jsonReader = new JsonReader(fileReader)) {
                return new Gson().fromJson(jsonReader, clazz);
            }
        } catch (IOException e) {
            throw new PluginException("IOException: " + e.getMessage());
        }
    }
}
