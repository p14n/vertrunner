package com.p14n.vertx;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.rolling.RollingFileAppender;
import org.apache.log4j.rolling.TimeBasedRollingPolicy;
import org.apache.log4j.PatternLayout;

public class LoggingSetup {
	
	public static void setupLogging(String filename,String archivePattern,Level l){

		PatternLayout layout = new PatternLayout("%d{ISO8601} %p [%t] %c [%X{req}] %m%n");
		RollingFileAppender appender = new RollingFileAppender();
		appender.setName("rootfile");
		appender.setFile(filename);
		appender.setLayout(layout);
		TimeBasedRollingPolicy policy = new TimeBasedRollingPolicy();
		policy.setFileNamePattern(archivePattern);
		appender.setRollingPolicy(policy);
		appender.activateOptions();
		Logger root = Logger.getRootLogger();
		root.setLevel(l);
		root.addAppender(appender);
		root.info("Logging set up");

	}


}