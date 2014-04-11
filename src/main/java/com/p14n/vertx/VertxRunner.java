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
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.io.FileReader;
import java.io.BufferedReader;

/**
 * Created by Dean Pehrsson-Chapman
 * Date: 13/09/2013
 */
public class VertxRunner {

  public static PlatformManager cluster(Map<String,String> settings) {

    String cluster = settings.get("cluster");
    if(cluster!=null){
      System.out.println("Cluster values "+cluster);
      String[] clusterVals = cluster.split(",");

      int port = Integer.parseInt(clusterVals[0]);
      String host = clusterVals[1];

      Config cfg = new Config();
      ProgrammableClusterManagerFactory.setConfig(cfg);
      System.setProperty("vertx.clusterManagerFactory", ProgrammableClusterManagerFactory.class.getName());
      NetworkConfig network = cfg.getNetworkConfig();
      network.setPort(port);
      network.setPortAutoIncrement(false);
      Join join = network.getJoin();

      String members = settings.get("members");
      if(members!=null){
        for(String ip:members.split(",")){
          join.getTcpIpConfig().addMember(ip);
        }
      }
      join.getMulticastConfig().setEnabled(members==null);
      join.getTcpIpConfig().setEnabled(members!=null);

      if(clusterVals.length == 2)
        return PlatformLocator.factory.createPlatformManager(port, host);
      if(clusterVals.length == 4)
        return PlatformLocator.factory.createPlatformManager(port, host,Integer.parseInt(clusterVals[2]),clusterVals[3]);

    }
    return PlatformLocator.factory.createPlatformManager();
  }
    
  private static void setupLogging(Map<String,String> settings) {
    String[] logstuff = new String[]{"vertrunner.log","arch/vertrunner.log%d","DEBUG"};
    if(settings.containsKey("log")){
      System.out.println("Log values "+settings.get("log"));
      logstuff = settings.get("log").split(",");
    }
    LoggingSetup.setupLogging(logstuff[0],logstuff[1],Level.toLevel(logstuff[2]));
    System.setProperty(
            "org.vertx.logger-delegate-factory-class-name",
            "org.vertx.java.core.logging.impl.SLF4JLogDelegateFactory"
    );
  }

  public static List<String> findArgumentsFromArgumentFiles(String args[]){
    List<String> params = new ArrayList<String>();
    for(String arg:args){
      System.out.println("Examine "+arg);
      File f = new File(arg);
      if(f.exists() && !f.isDirectory()){
        System.out.println("Reading for args "+arg);
        try {
          BufferedReader b = new BufferedReader(new FileReader(f));
          for(String line = "";line!=null;line=b.readLine()){
            System.out.println("Found arg "+line);
            params.add(line);
          }
          b.close();
        } catch (Exception e){
          e.printStackTrace();
        }
      } else {
        params.add(arg);
      }
    }
    return params;
  }
    
  public static void main(String params[]) throws BrokenBarrierException, InterruptedException {

    List<String> args = findArgumentsFromArgumentFiles(params);

    Map<String,String> settings = filterSettings(args);
    setupLogging(settings);

    //PlatformManager pm = PlatformLocator.factory.createPlatformManager();
    PlatformManager pm = cluster(settings);


    deployAll(pm,args);
    //pm.undeployAll(null);
    Thread.currentThread().join();
    


  }

  public static Map<String,String> filterSettings(List<String> params){
    Map<String,String> settings = new HashMap<String,String>();
    for(int i=0;i<params.size();i++){
      String param = params.get(i);
      int index = param.indexOf('=');
      if(index>-1){
        System.out.println("Found setting "+param);
        settings.put(param.substring(0,index),param.substring(index+1,param.length()));
        params.remove(i--);
      }
    }
    for(Map.Entry<String,String> ent:settings.entrySet()){
      System.out.println(ent.getKey()+" = "+ent.getValue());
    }
    return settings;
  }

  public static PlatformManager deployAll(PlatformManager pm, List<String> names) {
    CountDownLatch latch = new CountDownLatch(names.size());
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
