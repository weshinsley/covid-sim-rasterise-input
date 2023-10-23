package com.mrc.rasterise.export;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Map;

import javax.imageio.ImageIO;

import com.mrc.rasterise.population.Population;
import com.mrc.rasterise.shapefile.Raster;

public class Images {

  public static void make_shape_png(Raster unit_raster, String file) throws Exception {
    
    // Make a hideous PNG for debugging, showing the shapefiles in different colours.
    
    // First find the extents of pixels in the HashMap, and max unit number
    
    int minx = Integer.MAX_VALUE;
    int miny = Integer.MAX_VALUE;
    int maxx = Integer.MIN_VALUE;
    int maxy = Integer.MIN_VALUE;
    int maxv = -1;
    
    for (Map.Entry<Long, Integer> pix : unit_raster.data.entrySet()) {
      int j = (int) Math.floor(pix.getKey() / Raster.WIDTH);
      int i = (int) (pix.getKey() - (j * Raster.WIDTH));
      minx = Math.min(minx, i);
      maxx = Math.max(maxx,  i);
      miny = Math.min(miny,  j);
      maxy = Math.max(maxy,  j);
      maxv = Math.max(maxv,  pix.getValue());
    }
    
    
    // Pre-choose some nasty colours that aren't too close to black 
    
    int[] cols = new int[maxv + 1];
    for (int i=0; i <= maxv; i++) 
      cols[i] = new Color((int) (32 + (Math.random() * 191)), (int) (32 + (Math.random() * 191)), 
          (int) (32 + (Math.random() * 191))).getRGB(); 
    
    // Plot.
    
    BufferedImage bi = new BufferedImage(1 + (maxx - minx), 1 + (maxy - miny), BufferedImage.TYPE_3BYTE_BGR);
    for (Map.Entry<Long, Integer> pix : unit_raster.data.entrySet()) {
      int j = (int) Math.floor(pix.getKey() / Raster.WIDTH);
      int i = (int) (pix.getKey() - (j * Raster.WIDTH));
      bi.setRGB(i - minx,  j - miny,  cols[pix.getValue()]);
    }
    ImageIO.write(bi,  "PNG",  new File(file));
  }
  
  public static void make_pop_png(String file, Raster unit_raster, Population pop) throws Exception {
    // Here, we'll make a population density image, clipped to the raster
    // of units. Note that this will probably fail for a really large image - 
    // problems with 43200x21600 pngs.
    
    int minx = Integer.MAX_VALUE;
    int miny = Integer.MAX_VALUE;
    int maxx = Integer.MIN_VALUE;
    int maxy = Integer.MIN_VALUE;
    int maxv = -1;
    
    // Find extents of the unit raster, and the largest population
    // for pixels within units.
    
    for (Map.Entry<Long, Integer> pix : unit_raster.data.entrySet()) {
      int j = (int) Math.floor(pix.getKey() / Raster.WIDTH);
      int i = (int) (pix.getKey() - (j * Raster.WIDTH));
      minx = Math.min(minx, i);
      maxx = Math.max(maxx,  i);
      miny = Math.min(miny,  j);
      maxy = Math.max(maxy,  j);
      if (pop.data.containsKey(pix.getKey())) {
        maxv = Math.max(maxv,  pop.data.get(pix.getKey()));
      }
    }
    
    
    // Create a grey-scale lookup palette - and then we'll use a log scale. 
    
    int[] cols = new int[256];
    for (int i=0; i <= 255; i++) 
      cols[i] = new Color(i, i, i).getRGB();
    
    double logmaxv = Math.log(maxv);

    // Plot. For each pixel in the unit_raster, if we have population for it, then
    // work out where the pixel is, and pick a colour from the 255-colour scale.
    
    BufferedImage bi = new BufferedImage(1 + (maxx - minx), 1 + (maxy - miny), BufferedImage.TYPE_3BYTE_BGR);
    for (Map.Entry<Long, Integer> pix : unit_raster.data.entrySet()) {
      if (pop.data.containsKey(pix.getKey())) {
        int popval = pop.data.get(pix.getKey());
        int j = (int) Math.floor(pix.getKey() / Raster.WIDTH);
        int i = (int) (pix.getKey() - (j * Raster.WIDTH));
        bi.setRGB(i - minx,  j - miny,  cols[(int) (255.0 * (Math.log(popval) / logmaxv))]);
      }
    }
    ImageIO.write(bi,  "PNG",  new File(file));
  }

}
