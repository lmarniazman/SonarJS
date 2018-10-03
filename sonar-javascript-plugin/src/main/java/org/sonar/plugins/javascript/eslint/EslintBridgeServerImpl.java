/*
 * SonarQube JavaScript Plugin
 * Copyright (C) 2011-2018 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.plugins.javascript.eslint;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.utils.TempFolder;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.nodejs.NodeCommand;
import org.sonarsource.nodejs.NodeCommandBuilder;

import static org.sonar.plugins.javascript.eslint.FileUtils.copyFromClasspath;
import static org.sonar.plugins.javascript.eslint.NetUtils.findOpenPort;
import static org.sonar.plugins.javascript.eslint.NetUtils.waitServerToStart;

public class EslintBridgeServerImpl implements EslintBridgeServer {

  private static final Logger LOG = Loggers.get(EslintBridgeServerImpl.class);

  private static final int DEFAULT_TIMEOUT_SECONDS = 10;
  private static final String DEFAULT_STARTUP_SCRIPT = "eslint-bridge/bin/server";
  private static final String NODE_MODULES = "node_modules";
  private static final String DEFAULT_BUNDLE = NODE_MODULES;

  private final OkHttpClient client;
  private final NodeCommandBuilder nodeCommandBuilder;
  private final int timeoutSeconds;
  private int port;
  private NodeCommand nodeCommand;
  private String startServerScript;
  private String bundleLocation;
  private Path deployLocation;

  public EslintBridgeServerImpl(NodeCommandBuilder nodeCommandBuilder, TempFolder tempFolder) {
    this(nodeCommandBuilder, tempFolder, DEFAULT_TIMEOUT_SECONDS, DEFAULT_STARTUP_SCRIPT, DEFAULT_BUNDLE);
  }

  EslintBridgeServerImpl(NodeCommandBuilder nodeCommandBuilder, TempFolder tempFolder, int timeoutSeconds,
                         String startServerScript,
                         String bundleLocation) {
    this.nodeCommandBuilder = nodeCommandBuilder;
    this.timeoutSeconds = timeoutSeconds;
    this.startServerScript = startServerScript;
    this.bundleLocation = bundleLocation;
    this.client = new OkHttpClient.Builder()
      .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
      .build();
    // name has to be "node_modules" for correct dependency resolution in NodeJS
    this.deployLocation = tempFolder.newDir(NODE_MODULES).toPath();
  }

  @Override
  public void deploy() throws IOException, URISyntaxException {
    long start = System.currentTimeMillis();
    LOG.debug("Deploying eslint-bridge into {}", deployLocation);
    copyFromClasspath(bundleLocation, deployLocation);
    LOG.debug("Deployment done in {}ms", System.currentTimeMillis() - start);
  }

  @Override
  public void startServer(SensorContext context) throws IOException {
    port = findOpenPort();

    File scriptFile = deployLocation.resolve(startServerScript).toFile();
    if (!scriptFile.exists()) {
      throw new IllegalStateException("Node.JS script to start eslint-bridge server doesn't exist: " + scriptFile.getAbsolutePath());
    }
    nodeCommand = nodeCommandBuilder
      .outputConsumer(message -> {
        if (message.startsWith("DEBUG")) {
          LOG.debug(message.substring(5).trim());
        } else {
          LOG.info(message);
        }
      })
      .minNodeVersion(6)
      .configuration(context.config())
      .script(scriptFile.getAbsolutePath())
      .scriptArgs(String.valueOf(port))
      .build();
    LOG.debug("Starting Node.js process to start eslint-bridge server at port " + port);
    nodeCommand.start();

    if (!waitServerToStart("localhost", port, timeoutSeconds * 1000)) {
      throw new IllegalStateException("Failed to start server (" + timeoutSeconds +"s timeout)");
    }

    LOG.debug("Server is started");
  }

  @Override
  public String call(String json) throws IOException {
    Request request = new Request.Builder()
      .url("http://localhost:" + port + "/analyze")
      .post(RequestBody.create(MediaType.get("application/json"), json))
      .build();

    try (Response response = client.newCall(request).execute()) {
      // in this case response.body() is never null (according to docs)
      return response.body().string();
    }
  }

  @Override
  public void clean() {
    if (nodeCommand != null) {
      nodeCommand.destroy();
      nodeCommand = null;
    }
  }

  @Override
  public String getCommandInfo() {
    if (nodeCommand == null) {
      return "Node.JS command to start eslint-bridge server was not built yet.";
    } else {
      return "Node.JS command to start eslint-bridge was: " + nodeCommand.toString();
    }
  }

}
