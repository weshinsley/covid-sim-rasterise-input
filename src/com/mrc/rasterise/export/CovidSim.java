package com.mrc.rasterise.export;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import com.mrc.rasterise.population.Population;
import com.mrc.rasterise.shapefile.Raster;
import com.mrc.rasterise.shapefile.ShapeList;

public class CovidSim {
    
  // The output we want for Covid Sim is in the form...
  //  lon             lat             pop    country   unit code
  //  5.516667        13.891666       209     10      103400
  
  // lon and lat are the lower-left corners of a 1/120 degree cell.
  // country 10 has been Nigeria in Covid Sim.

  // Additionally, we may have country codes that we want to use/
  // not necessarily starting at one. (eg, UK = 54)
  
  // Lastly, this is for ADM0,1,2 for now - other arrangements
  // will be ad hoc in any case. If there are more than 99
  // ADM2 units in a single ADM1, then an additional ADM1 will 
  // be made with the same name to accommodate. If we need more 
  // than 99 ADM1s (including any extras because of overflow of ADM2), 
  // then for now we'll abort, as this will need some manual intervention
  // both in terms of the data, and covid-sim itself.
  
  public static HashMap<String, String> pack_countries(String[] countries, String[] codes) throws Exception {
    HashMap<String, String> res = new HashMap<String, String>();
    if ((countries.length != codes.length) || (countries.length == 0)) {
      throw new Exception("Invalid country/code lengths for packing ("+countries.length+","+codes.length+")");
    }
    for (int i=0; i<countries.length; i++) {
      res.put(countries[i], codes[i]);
    }
    return res;
  }
  
  private static String[] generate_unit_ids(ShapeList sl, HashMap<String, String> country_lookup) throws Exception {
    
    boolean auto_countries = country_lookup.isEmpty();
    HashMap<String, ArrayList<String>> all_adm1_codes = new HashMap<String, ArrayList<String>>();
    HashMap<String, ArrayList<ArrayList<String>>> all_adm2_codes = new HashMap<String, ArrayList<ArrayList<String>>>();

    String[] covid_unit_ids = new String[sl.shapes.size()];
    String country_name = "";
    String country_code = "";
    String adm1_name = "";
    int adm1_code = -1;
    int adm2_code = -1;
    
    for (int i = 0; i < sl.shapes.size(); i++) {
      HashMap<String, String> meta = sl.shapes.get(i).metadata;
      String next_country = meta.get("NAME_0");
      String next_adm1_name = meta.get("NAME_1");
      String adm2_name = meta.get("NAME_2");
      
      // Different country encountered
      
      if (!next_country.equals(country_name)) {
        adm1_name = "";
        if (auto_countries) {
          if (!country_lookup.containsKey(next_country)) {
            int next_country_id = country_lookup.size() + 1;
            country_lookup.put(next_country, ((next_country_id < 10) ? "0" : "") + next_country_id);
            all_adm1_codes.put(next_country,  new ArrayList<String>());
            all_adm2_codes.put(next_country,  new ArrayList<ArrayList<String>>());
          }
        }
        
        if (country_lookup.containsKey(next_country)) {
          country_code = country_lookup.get(next_country);
          country_name = next_country;
          
        } else { // Must be non-auto, and country not included. So skip.
          country_name = "";
          continue;
        }
      }
      
      
      ArrayList<String> adm1_codes = all_adm1_codes.get(country_name);
      ArrayList<ArrayList<String>> adm2_codes = all_adm2_codes.get(country_name);

      // New adm1 encountered

      if (!next_adm1_name.equals(adm1_name)) {
        if (!adm1_codes.contains(next_adm1_name)) {
          if (adm1_codes.size() >= 99) {
            throw new Exception("100+ admin 1 units in "+country_name);
          }
          adm1_codes.add(next_adm1_name);
          adm2_codes.add(new ArrayList<String>());
        }
        adm1_name = next_adm1_name;
        adm1_code = adm1_codes.lastIndexOf(next_adm1_name);
      }
      
      // Lookup or create adm2 list
      
      ArrayList<String> adm2_for_adm1 = adm2_codes.get(adm1_code);
      
      if (!adm2_for_adm1.contains(adm2_name)) {
        if (adm2_for_adm1.size() >= 99) {
          adm1_codes.add(next_adm1_name);
          if (adm1_codes.size() >= 100) {
            throw new Exception("100+ admin 1 units in "+country_name);
          }
          adm1_code = adm1_codes.lastIndexOf(next_adm1_name);
          adm2_codes.add(new ArrayList<String>());
          adm2_for_adm1 = adm2_codes.get(adm1_code);
        }
        adm2_for_adm1.add(adm2_name);
      }
      adm2_code = adm2_for_adm1.lastIndexOf(adm2_name);
      
      covid_unit_ids[i] = country_code + ((adm1_code < 10) ? "0" : "") + adm1_code + 
                                         ((adm2_code < 10) ? "0" : "") + adm2_code;
    }
    return covid_unit_ids;
    
  }
  
  
  public static void export(Raster map, ShapeList sl, Population pop, 
                     String file, HashMap<String, String> countries) throws Exception {
    
    
    // For the countries we want, create a mapping from NAME_0, NAME_1, NAME_2 into
    // the six digit code ccaabb.
    
    String[] covid_unit_ids = generate_unit_ids(sl, countries);
        
    // We want a 6-digit code, first two are country, next two adm1 and next two adm2
    // Country might not be contiguous, but adm1 and adm2 codes will be.
            
    PrintWriter PW = new PrintWriter(new FileWriter(file));
    double cell_size = 1.0 / Raster.RESOLUTION;
    double top_lat = 90 - cell_size;
    
    // For each pixel we assigned to a particular unit...
    
    for (Map.Entry<Long, Integer> pix : map.data.entrySet()) {
      long key = pix.getKey();
      int unit = pix.getValue();
      
      if (covid_unit_ids[unit] != null) {
      
      // Lookup the name/adm1/adm2 for that unit.
      // If we have population data for that pixel
      // then write a row of data
      
        if (pop.data.containsKey(key)) {
          long count = pop.data.get(key);
          int y = (int) Math.floor(key / Raster.WIDTH);
          int x = (int) key - (y * Raster.WIDTH);
          float lon = (float) (-180.0 + (x * cell_size));
          float lat = (float) (top_lat - (y * cell_size));
          PW.println(lon + "\t" + lat + "\t" + count + "\t" + covid_unit_ids[unit].substring(0, 2) + "\t" +
                                                              covid_unit_ids[unit]);
        }
      }
    }
    PW.close();
  }
  
  public static void export(Raster map, ShapeList sl, Population pop, String file) throws Exception {
    export(map, sl, pop, file, new HashMap<String, String>());
  }
}
