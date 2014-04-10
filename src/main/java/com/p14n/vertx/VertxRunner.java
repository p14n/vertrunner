package com.p14n.vertx;

import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.PropertyConfigurator;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.platform.PlatformLocator;
import org.vertx.java.platform.PlatformManager;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Properties;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import org.apache.log4j.Level;
import com.hazelcast.config.*;
import org.vertx.java.spi.cluster.impl.hazelcast.ProgrammableClusterManagerFactory;

/**
 * Created by Dean Pehrsson-Chapman
 * Date: 13/09/2013
 */
public class VertxRunner {

  public static void cluster(int port,String... ips){

    Config cfg = new Config();
    ProgrammableClusterManagerFactory.setConfig(cfg);
    System.setProperty("vertx.clusterManagerFactory", ProgrammableClusterManagerFactory.class.getName());

    NetworkConfig network = cfg.getNetworkConfig();
    network.setPort(port);
    network.setPortAutoIncrement(false);
    Join join = network.getJoin();
    join.getMulticastConfig().setEnabled(false);
    for(String ip:ips){
      join.getTcpIpConfig().addMember(ip);
    }
    join.getTcpIpConfig().setEnabled(true);

  }
    
  private static void setupLogging() {
    LoggingSetup.setupLogging("colqo.log","arch/colqo.log%d",Level.DEBUG);
    System.setProperty(
            "org.vertx.logger-delegate-factory-class-name",
            "org.vertx.java.core.logging.impl.SLF4JLogDelegateFactory"
    );
  }
    
  public static void main(String args[]) throws BrokenBarrierException, InterruptedException {

    setupLogging();

    //PlatformManager pm = PlatformLocator.factory.createPlatformManager();
    cluster(5900);

    PlatformManager pm = PlatformLocator.factory.createPlatformManager(5900, "::1");
    deployAll(pm,args);
    //pm.undeployAll(null);
    Thread.currentThread().join();
    
  }

  public static PlatformManager deployAll(PlatformManager pm, String... names) {
    CountDownLatch latch = new CountDownLatch(names.length);
    int index = 1;
    for (String name : names) {
      int colon = name.indexOf(":");
      String cp = null;
      if(colon>0){

        
        cp = name.substring(0,colon);
        name = name.substring(colon+1);
      }
      File f = new File(name);
      System.out.println(f.getAbsolutePath());
      if (f.exists()) {
        pm.deployModuleFromClasspath("dev~" + name + "~1",null, 1, getClasspath(cp,name),
                createResultHandler(name, latch));
      } else {
        pm.deployModule(name, null, 1, createResultHandler(name, latch));
      }
    }
    try {
      System.out.println("Waiting for deployment to complete");
      latch.await();
      System.out.println("Deployment complete");
    } catch (InterruptedException e) {
      throw new RuntimeException("", e);
    }
    return pm;
  }

  private static AsyncResultHandler<String> createResultHandler(final String name, final CountDownLatch latch) {
    return new AsyncResultHandler<String>() {
      public void handle(AsyncResult<String> asyncResult) {
        if (asyncResult.succeeded()) {
          System.out.println(name + " deployment ID is " + asyncResult.result());
        } else {
          System.out.println(name + "deployment failed, ID is " + asyncResult.result());
          asyncResult.cause().printStackTrace();
        }
        try {
          latch.countDown();
        } catch (Exception e) {
        }
      }
    };
  }
  private static URL[] getClasspath(String cp,String root) {
    URL[] urls= null;
    if("lein".equals(cp)){
      urls = getLeinClasspath(root);
    } else {
      urls = getGradleClasspath(root);      
    }
    for(URL url:urls){
      System.out.println(url);
    }
    return urls;
  }
  private static URL[] getLeinClasspath(String root) {
    try {
      File libDir = new File(root + "/lib/");
      File[] jars = libDir.listFiles();
      URL[] urls = null;
      System.out.println("Add jars");
      if (jars != null) {
        urls = new URL[jars.length + 3];
        int index = 3;
        for (File jar : jars) {
          System.out.println(" jar: "+jar.getAbsolutePath());
          urls[index++] = jar.toURI().toURL();
        }
      } else {
        urls = new URL[3];
      }
      urls[0] = new URL("file:" + root + "/target/classes/");
      urls[1] = new URL("file:" + root + "/resources/");
      urls[2] = new URL("file:" + root + "/src/");
      return urls;
    } catch (MalformedURLException e) {
      throw new RuntimeException(e);
    }
  }
  private static URL[] getGradleClasspath(String root) {
    try {
      File libDir = new File(root + "/build/output/lib/");
      File[] jars = libDir.listFiles();
      URL[] urls = null;

      if (jars != null) {
        System.out.println("Add jars");
        urls = new URL[jars.length + 2];
        int index = 2;
        for (File jar : jars) {
          System.out.println(" jar: "+jar.getAbsolutePath());
          urls[index++] = jar.toURI().toURL();
        }
      } else {
        urls = new URL[2];
      }
      urls[0] = new URL("file:" + root + "/build/classes/main/");
      urls[1] = new URL("file:" + root + "/build/resources/main/");
      return urls;
    } catch (MalformedURLException e) {
      throw new RuntimeException(e);
    }
  }
}
