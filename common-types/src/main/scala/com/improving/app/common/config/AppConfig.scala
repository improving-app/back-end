package com.improving.app.common.config

import com.typesafe.config.{Config, ConfigFactory}

class AppConfig(useDyanmo: Boolean = false) {
  val configFile: String = if (useDyanmo) {
    "application-dynamo-journal.conf"
  } else {
    "application-cass-journal.conf"
  }

  def loadConfig: Config = ConfigFactory
    .load(configFile)
    .withFallback(ConfigFactory.defaultApplication())
}
