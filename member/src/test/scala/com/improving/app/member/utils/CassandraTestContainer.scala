package com.improving.app.member.utils

import com.dimafeng.testcontainers.{CassandraContainer, ForAllTestContainer}
import org.scalatest.Suite
import scala.jdk.CollectionConverters.IterableHasAsJava

trait CassandraTestContainer extends ForAllTestContainer with LoanedActorSystem { self: Suite =>
  def cassandraInitScriptPath: String

  abstract override def configOverrides: Map[String, Any] =
    super.configOverrides ++
      Map(
        "datastax-java-driver.basic.contact-points" ->
          List(s"${container.cassandraContainer.getHost}:${container.cassandraContainer.getFirstMappedPort
            .intValue()}").asJava,
        "datastax-java-driver.basic.load-balancing-policy.local-datacenter" -> "datacenter1",
        "datastax-java-driver.advanced.auth-provider.class" -> "PlainTextAuthProvider",
        "datastax-java-driver.advanced.auth-provider.username" -> container.username,
        "datastax-java-driver.advanced.auth-provider.password" -> container.password,
        "datastax-java-driver.advanced.reconnect-on-init" -> "true"
      )

  override val container: CassandraContainer =
    CassandraContainer(
      //DockerImageName.parse("oracleinanutshell/oracle-xe-11g"),
      initScript = cassandraInitScriptPath
    )
}
