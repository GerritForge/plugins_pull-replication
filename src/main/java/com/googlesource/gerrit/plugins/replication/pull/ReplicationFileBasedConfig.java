// Copyright (C) 2013 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.googlesource.gerrit.plugins.replication.pull;

import static java.util.stream.Collectors.toList;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.annotations.PluginData;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.git.WorkQueue;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.util.FS;

@Singleton
public class ReplicationFileBasedConfig implements ReplicationConfig {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private List<Source> sources;
  private final SitePaths site;
  private Path cfgPath;
  private boolean replicateAllOnPluginStart;
  private boolean defaultForceUpdate;
  private final FileBasedConfig config;
  private final Path pluginDataDir;

  @Inject
  public ReplicationFileBasedConfig(
      SitePaths site, SourceFactory sourceFactory, @PluginData Path pluginDataDir)
      throws ConfigInvalidException, IOException {
    this.site = site;
    this.cfgPath = site.etc_dir.resolve("pull-replication.config");
    this.config = new FileBasedConfig(cfgPath.toFile(), FS.DETECTED);
    this.sources = allSources(sourceFactory);
    this.pluginDataDir = pluginDataDir;
  }

  /*
   * (non-Javadoc)
   * @see
   * com.googlesource.gerrit.plugins.replication.pull.ReplicationConfig#getSources
   * (com.googlesource.gerrit.plugins.replication.pull.ReplicationConfig.FilterType)
   */
  @Override
  public List<Source> getSources(FilterType filterType) {
    Predicate<? super Source> filter;
    switch (filterType) {
      case PROJECT_CREATION:
        filter = source -> source.isCreateMissingRepos();
        break;
      case PROJECT_DELETION:
        filter = source -> source.isReplicateProjectDeletions();
        break;
      case ALL:
      default:
        filter = source -> true;
        break;
    }
    return sources.stream().filter(Objects::nonNull).filter(filter).collect(toList());
  }

  private List<Source> allSources(SourceFactory sourceFactory)
      throws ConfigInvalidException, IOException {
    if (!config.getFile().exists()) {
      logger.atWarning().log("Config file %s does not exist; not replicating", config.getFile());
      return Collections.emptyList();
    }
    if (config.getFile().length() == 0) {
      logger.atInfo().log("Config file %s is empty; not replicating", config.getFile());
      return Collections.emptyList();
    }

    try {
      config.load();
    } catch (ConfigInvalidException e) {
      throw new ConfigInvalidException(
          String.format("Config file %s is invalid: %s", config.getFile(), e.getMessage()), e);
    } catch (IOException e) {
      throw new IOException(
          String.format("Cannot read %s: %s", config.getFile(), e.getMessage()), e);
    }

    replicateAllOnPluginStart = config.getBoolean("gerrit", "replicateOnStartup", false);

    defaultForceUpdate = config.getBoolean("gerrit", "defaultForceUpdate", false);

    ImmutableList.Builder<Source> sources = ImmutableList.builder();
    for (RemoteConfig c : allRemotes(config)) {
      if (c.getURIs().isEmpty()) {
        continue;
      }

      // If source is not set, assume everything.
      if (c.getFetchRefSpecs().isEmpty()) {
        c.addFetchRefSpec(
            new RefSpec()
                .setSourceDestination("refs/*", "refs/*")
                .setForceUpdate(defaultForceUpdate));
      }

      Source source = sourceFactory.create(new SourceConfiguration(c, config));

      if (!source.isSingleProjectMatch()) {
        for (URIish u : c.getURIs()) {
          if (u.getPath() == null || !u.getPath().contains("${name}")) {
            throw new ConfigInvalidException(
                String.format(
                    "remote.%s.url \"%s\" lacks ${name} placeholder in %s",
                    c.getName(), u, config.getFile()));
          }
        }
      }

      sources.add(source);
    }
    return sources.build();
  }

  /* (non-Javadoc)
   * @see com.googlesource.gerrit.plugins.replication.pull.ReplicationConfig#isReplicateAllOnPluginStart()
   */
  @Override
  public boolean isReplicateAllOnPluginStart() {
    return replicateAllOnPluginStart;
  }

  private static List<RemoteConfig> allRemotes(FileBasedConfig cfg) throws ConfigInvalidException {
    Set<String> names = cfg.getSubsections("remote");
    List<RemoteConfig> result = Lists.newArrayListWithCapacity(names.size());
    for (String name : names) {
      try {
        result.add(new RemoteConfig(cfg, name));
      } catch (URISyntaxException e) {
        throw new ConfigInvalidException(
            String.format("remote %s has invalid URL in %s", name, cfg.getFile()));
      }
    }
    return result;
  }

  /* (non-Javadoc)
   * @see com.googlesource.gerrit.plugins.replication.pull.ReplicationConfig#isEmpty()
   */
  @Override
  public boolean isEmpty() {
    return sources.isEmpty();
  }

  @Override
  public Path getEventsDirectory() {
    String eventsDirectory = config.getString("replication", null, "eventsDirectory");
    if (!Strings.isNullOrEmpty(eventsDirectory)) {
      return site.resolve(eventsDirectory);
    }
    return pluginDataDir;
  }

  Path getCfgPath() {
    return cfgPath;
  }

  @Override
  public int shutdown() {
    int discarded = 0;
    for (Source cfg : sources) {
      discarded += cfg.shutdown();
    }
    return discarded;
  }

  FileBasedConfig getConfig() {
    return config;
  }

  @Override
  public void startup(WorkQueue workQueue) {
    for (Source cfg : sources) {
      cfg.start(workQueue);
    }
  }
}
