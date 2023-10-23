package com.mrc.rasterise.population;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.file.Paths;
import java.util.HashMap;

public class Population {
  public HashMap<Long, Integer> data = new HashMap<Long, Integer>();
  static long WIDTH = 43200;
  static long HEIGHT = 21600;
  int skip_north = 0;
  int max_value;
  int min_value;
  long total;
  
  // Landscan 2000      had dimensions 43200x21120
  // Landscan 2001-2012 has dimensions 43200x20880
  // Landscan 2013-     has dimensions 43200x21600

  // In all cases, it starts from the bottom and misses the Northern-most. 
  // But we'll look at the HDR file anyway.
  
  private void _findMetadata(String file) throws Exception {
    File f = new File(file + ".hdr");
    if (!f.exists()) {
      throw new FileNotFoundException("Header for population file "+file+" not found");
    }
    
    BufferedReader br = new BufferedReader(new FileReader(f));
    HashMap<String, String> metadata = new HashMap<String, String>();
    String s = br.readLine();
    while (s != null) {
      String[] bits = s.split("\\s+");
      metadata.put(bits[0],  bits[1]);
      s = br.readLine();
    }
    br.close();
    if (metadata.containsKey("NROWS")) {
      skip_north = 21600 - Integer.parseInt(metadata.get("NROWS"));
    } else {
      throw new Exception("NROWS not found in " + file + ".hdr");
    }
  }
  
  public static Population loadPopulation(String file) throws Exception {
    Population P = new Population();
    P._findMetadata(file);
    P.min_value = Integer.MAX_VALUE;
    P.max_value = Integer.MIN_VALUE;
    File f = new File(file + ".bil");
    if (!f.exists()) {
      throw new FileNotFoundException("Population file " + f + " not found");
    }
    FileChannel fc = FileChannel.open(Paths.get(file + ".bil"));
    for (long j = P.skip_north; j < HEIGHT; j++) {
      MappedByteBuffer mbb = fc.map(MapMode.READ_ONLY, (long) (4L * (j - P.skip_north) * WIDTH), WIDTH * 4L);
      for (long i = 0; i < WIDTH; i++) {
        int val = (int) Math.max(0, Integer.reverseBytes(mbb.getInt()));
        if (val > 0) {
          P.min_value = Math.min(P.min_value, val);
          P.max_value = Math.max(P.max_value, val);
          P.total += val;
          P.data.put((j * (long) WIDTH) + i, val);
        }
      }
      mbb.clear();
    }
    fc.close();
    System.out.println("Pop read. Min = "+P.min_value+", Max = "+P.max_value+", Total = "+P.total);
    return P;
  }
  
  
  int getPopulation(long i, long j) {
    long hash = (j * (long) WIDTH) + i;
    return (data.containsKey(hash)) ? data.get(hash) : 0; 
  }
}
