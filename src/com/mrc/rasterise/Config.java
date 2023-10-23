package com.mrc.rasterise;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Paths;

import org.tomlj.Toml;
import org.tomlj.TomlParseResult;

public class Config  {

  TomlParseResult toml;

  void validate() throws Exception {
    if (!toml.contains("population")) {
      throw new Exception("population not found in config");
    }

    if (!new File(toml.getString("population")).exists()) {
      throw new FileNotFoundException("Population file not found : "+toml.getString("population"));
    }
  }

  public Config(String file) throws Exception {
    if (!new File(file).exists()) {
      throw new FileNotFoundException("Config file not found: "+file);
    }

    toml = Toml.parse(Paths.get(file));

    int errorCount = toml.errors().size();
    if (errorCount > 0) {
      StringBuilder sb = new StringBuilder ();
      toml.errors().forEach(error -> sb.append(sb+"\n"));
      throw new Exception("Errors in config file: \n"+ sb.toString());
    }

    validate();
  }
}

