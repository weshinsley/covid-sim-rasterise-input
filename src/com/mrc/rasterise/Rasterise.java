package com.mrc.rasterise;

import java.io.InputStream;

public class Rasterise {

  public int check_args(String[] args) throws Exception {
    if (args.length != 1) {
      throw new Exception("Requires one argument: config.toml");
    }
    return 0;
  }

  public int start(InputStream input, String[] args) throws Exception {
    check_args(args);
    Config config = new Config(args[0]);
    System.out.println(config.toml.get("population"));
    return 0;
  }

  public static void main(String[] args) throws Exception {
    Rasterise r = new Rasterise();
    r.start(System.in, args);
  }

}
