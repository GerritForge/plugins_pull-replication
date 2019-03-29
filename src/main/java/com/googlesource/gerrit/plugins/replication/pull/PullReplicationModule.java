// Copyright (C) 2012 The Android Open Source Project
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

import static com.googlesource.gerrit.plugins.replication.pull.StartFetchReplicationCapability.START_REPLICATION;

import org.eclipse.jgit.transport.SshSessionFactory;

import com.google.gerrit.extensions.annotations.Exports;
import com.google.gerrit.extensions.config.CapabilityDefinition;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.server.events.EventTypes;
import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.google.inject.internal.UniqueAnnotations;
import com.googlesource.gerrit.plugins.replication.AdminApiFactory;
import com.googlesource.gerrit.plugins.replication.CredentialsFactory;
import com.googlesource.gerrit.plugins.replication.RemoteSiteUser;
import com.googlesource.gerrit.plugins.replication.ReplicationSshSessionFactoryProvider;

class PullReplicationModule extends AbstractModule {
  @Override
  protected void configure() {
    bind(SourceFactory.class).in(Scopes.SINGLETON);
    bind(PullReplicationQueue.class).in(Scopes.SINGLETON);
    bind(LifecycleListener.class)
        .annotatedWith(UniqueAnnotations.create())
        .to(PullReplicationQueue.class);

    bind(OnStartStop.class).in(Scopes.SINGLETON);
    bind(LifecycleListener.class).annotatedWith(UniqueAnnotations.create()).to(OnStartStop.class);
    bind(LifecycleListener.class)
        .annotatedWith(UniqueAnnotations.create())
        .to(PullReplicationLogFile.class);
    bind(CredentialsFactory.class)
        .to(AutoReloadSecureCredentialsFactoryDecorator.class)
        .in(Scopes.SINGLETON);
    bind(CapabilityDefinition.class)
        .annotatedWith(Exports.named(START_REPLICATION))
        .to(StartFetchReplicationCapability.class);

    install(new FactoryModuleBuilder().build(FetchAll.Factory.class));
    install(new FactoryModuleBuilder().build(RemoteSiteUser.Factory.class));
    install(new FactoryModuleBuilder().build(ReplicationState.Factory.class));

    bind(ReplicationConfig.class).to(AutoReloadConfigDecorator.class);
    bind(ReplicationStateListener.class).to(ReplicationStateLogger.class);

    EventTypes.register(FetchRefReplicatedEvent.TYPE, FetchRefReplicatedEvent.class);
    EventTypes.register(FetchRefReplicationDoneEvent.TYPE, FetchRefReplicationDoneEvent.class);
    EventTypes.register(FetchReplicationScheduledEvent.TYPE, FetchReplicationScheduledEvent.class);
    bind(SshSessionFactory.class).toProvider(ReplicationSshSessionFactoryProvider.class);

    bind(AdminApiFactory.class);
  }
}
