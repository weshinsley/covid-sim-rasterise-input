package com.mrc.rasterise.shapefile;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public class Raster {
  
  private static BufferedImage buffer = null;
  private static Graphics2D g2d = null;
  public static final int WIDTH = 43200;
  public static final int HEIGHT = 21600;
  public static final double RESOLUTION = 120.0;
  private static final int INT_SCALER = 2400;
  private static final double LON_WL = -180.0;
  //private static final double LAT_SB = -90.0;
  private static final double LAT_NT = 90.0;
  
  public HashMap<Long, Integer> data = new HashMap<Long, Integer>();

  private static void clearBuffer() {
    if (buffer != null) {
      g2d = null;
      buffer = null;
      System.gc();
    }
  }
  
  private static void initBuffer() {
    if (buffer!=null) {
      clearBuffer();
    }
    buffer = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
    g2d = (Graphics2D) buffer.getGraphics();
    g2d.setColor(Color.BLACK);
    g2d.fillRect(0,0, WIDTH, HEIGHT);
  }

  
  private static void rasterisePoly(ArrayList<DPolygon> polys, int[][] unit_ids, 
      HashMap<Long, HashSet<Integer>> contentions, int code, int null_unit) {
    
    if (buffer == null) {
      initBuffer();
    }
    
    // Find bounds of this shape first.
    int maxx = Integer.MIN_VALUE;
    int maxy = Integer.MIN_VALUE;
    int minx = Integer.MAX_VALUE;
    int miny = Integer.MAX_VALUE;
    int black = Color.black.getRGB();
    g2d.setColor(Color.WHITE);
    for (int i = 0; i < polys.size(); i++) {
      DPolygon p = polys.get(i);
      if (p.clockwise) {
        DPolygon p2 = new DPolygon();
        for (int j = 0; j < p.npoints; j++) {
          p2.addPoint((int)((-LON_WL + ((double)p.xpoints[j] / INT_SCALER)) * RESOLUTION),
                      (int)((LAT_NT - ((double)p.ypoints[j] / INT_SCALER)) * RESOLUTION));
          
          minx = Math.min(minx, p2.xpoints[p2.npoints-1]);
          maxx = Math.max(maxx, p2.xpoints[p2.npoints-1]);
          miny = Math.min(miny, p2.ypoints[p2.npoints-1]);
          maxy = Math.max(maxy, p2.ypoints[p2.npoints-1]);           
        }
       
        g2d.fillPolygon(p2);
      }
    }
    g2d.setColor(Color.BLACK);
    for (int i = 0; i < polys.size(); i++) {
      DPolygon p = polys.get(i);
      if (!p.clockwise) {
        DPolygon p2 = new DPolygon();
        for (int j = 0; j < p.npoints; j++) {
          p2.addPoint((int)((-LON_WL + ((double)p.xpoints[j] / INT_SCALER)) * RESOLUTION),
                      (int)((LAT_NT -  ((double)p.ypoints[j] / INT_SCALER)) * RESOLUTION));
          
          minx = Math.min(minx, p2.xpoints[p2.npoints - 1]);
          maxx = Math.max(maxx, p2.xpoints[p2.npoints - 1]);
          miny = Math.min(miny, p2.ypoints[p2.npoints - 1]);
          maxy = Math.max(maxy, p2.ypoints[p2.npoints - 1]);          
        }
        g2d.fillPolygon(p2);
      }
    }

    maxx = Math.min(WIDTH - 1, maxx);
    maxy = Math.min(HEIGHT - 1, maxy);
    for (int i = minx; i <= maxx; i++) {
      for (int j = miny; j <= maxy; j++) {
        long hash_index = (j * WIDTH) + i;
        if (buffer.getRGB(i, j) != black) {
          if (unit_ids[j][i] == null_unit) unit_ids[j][i] = code;
          else if (unit_ids[j][i] != code) {
            if (!contentions.containsKey(hash_index)) {
              HashSet<Integer> contenders = new HashSet<Integer>();
              contenders.add(code);
              contenders.add(unit_ids[j][i]);
              contentions.put(hash_index, contenders);

            } else {
              HashSet<Integer> contenders = contentions.get(hash_index);
              if (!contenders.contains(code)) contenders.add(code);
              if (!contenders.contains(unit_ids[j][i])) contenders.add(unit_ids[j][i]); 
            }
          }
          buffer.setRGB(i, j, black);
        }
      }
    }
  }
 
  public static Raster rasterise(ShapeList shapelist) {
    
    // This is the map we're going to draw polygons onto
   
    int[][] map = new int[HEIGHT][WIDTH];

    for (int j = 0; j < HEIGHT; j++)
      for (int i = 0; i < WIDTH; i++)
        map[j][i] = -1;
    
    // And in here, for any pixels where there are contentions, (ie, more than one
    // shape wants to own a pixel), we'll hash the pixel into a Long, and store for 
    // that index a set of unit ids that are contending for the pixel. 
    
    HashMap<Long, HashSet<Integer>> contentions = new HashMap<Long, HashSet<Integer>>();

    for (int i = 0; i < shapelist.shapes.size(); i++) {
      ShapeUnit su = shapelist.shapes.get(i);
      rasterisePoly(su.polygons, map, contentions, i, -1);
    }
    
    resolveContentions(shapelist, map, contentions);
    contentions.clear();
    
    Raster R = new Raster();
    for (int j = 0; j < HEIGHT; j++)
      for (int i = 0; i < WIDTH; i++)
        if (map[j][i] != -1)
          R.data.put(((long) j * WIDTH) + i, map[j][i]);
    
    return R;
  }

  // For each pixel that a number of units all want to own,
  // Zoom in and decide how many pixels would fit in each polygon.
  // Whoever has the most wins the pixel.
  
  private static void resolveContentions(ShapeList shapelist, int[][] map, 
                                HashMap<Long, HashSet<Integer>> contentions) {
  
    Rectangle cell = new Rectangle();

    // For each pixel where we have some contention.

    // Each entry is a hash of (j,i), and a set of integers which are the
    // units competing for the pixel.
    
    for (Map.Entry<Long, HashSet<Integer>> contention : contentions.entrySet()) {
    
      int j = (int) Math.floor(contention.getKey() / WIDTH);
      int i = (int) (contention.getKey() - (j * WIDTH));
      
      int xleft = (int) (((i / RESOLUTION) + LON_WL) * INT_SCALER);
      int ybottom = (int) (((((HEIGHT - 1) - j) / RESOLUTION) - LAT_NT) * INT_SCALER);
      int ytop = (int) (ybottom - (INT_SCALER / RESOLUTION));
      int rectsize = (int) (INT_SCALER / RESOLUTION);
      cell.setBounds(xleft, ytop, rectsize, rectsize);
      int max_score = 0;
      int best_id = -1;
      
      // Begin by putting an 9x9 (8+1 x 8+1) grid on top of the pixel that is
      // disputed. See how many of those pixels report as belonging to each
      // shape with a claim on the pixel...
      
      int micro_res = 8;
      while (true) {
        boolean draw = false;
        best_id = -1;
        max_score = -1;
        for (int unit_index : contention.getValue()) {
          ArrayList<DPolygon> adp = shapelist.shapes.get(unit_index).polygons;
          
          for (int _l = 0; _l < adp.size(); _l++) {
            int score = 0;
            DPolygon dp = adp.get(_l);
            if (dp.clockwise) {
              if (dp.intersects(cell)) {
                double spacer = INT_SCALER / (float) micro_res;
                for (int p = 0; p <= micro_res; p++) 
                  for (int q = 0; q <= micro_res; q++) 
                    if (dp.contains(xleft + (p * spacer), ytop + (q * spacer))) 
                      score++;

                if (score == max_score) draw = true;
                else if (score > max_score) {
                  draw = false;
                  max_score = score;
                  best_id = unit_index;
                }
              }
            }
          }
        }
        if (draw) micro_res *= 2;
        else break;
      }
      map[j][i] = best_id;
    }
  }
}
