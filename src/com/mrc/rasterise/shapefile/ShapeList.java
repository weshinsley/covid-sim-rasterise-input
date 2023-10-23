package com.mrc.rasterise.shapefile;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;

public class ShapeList {
  public ArrayList<ShapeUnit> shapes = new ArrayList<ShapeUnit>();
  
  public void append(ShapeList sf) {
    this.shapes.addAll(sf.shapes);
  }

  public static ShapeList load(String fileStub, int level, String _name_field, String _num_field) throws Exception {
    String[] name_field = _name_field.split(";");
    String[] num_field = _num_field.split(";");
   
    if ((name_field.length != num_field.length) || (name_field.length != level + 1) || 
        (num_field.length != level + 1)) {
      throw new Exception("Fields must be semi-colon separated, and same length as number of levels wanted");
    }
    
    ShapeList S = new ShapeList();

    DataInputStream dbf = new DataInputStream(new BufferedInputStream(new FileInputStream(fileStub + ".dbf")));
    DataInputStream shp = new DataInputStream(new BufferedInputStream(new FileInputStream(fileStub + ".shp")));

    ArrayList<String> field_names = new ArrayList<String>();
    ArrayList<Character> field_types = new ArrayList<Character>();
    ArrayList<Integer> field_lengths = new ArrayList<Integer>();

    dbf.readByte();                                              // byte file_type =
    dbf.readByte();                                              // byte file_type =
    dbf.readByte();                                              // byte mod_mm =
    dbf.readByte();                                              // byte mod_dd =
    Integer.reverseBytes(dbf.readInt());                         // int no_recs =
    Short.reverseBytes(dbf.readShort());                         // short first_rec =
    Short.reverseBytes(dbf.readShort());                         // short rec_length =

    for (int i = 0; i < 16; i++) dbf.readByte();
    dbf.readByte();                                              // byte flags =
    dbf.readByte();                                              // byte codepage =
    for (int i = 0; i < 2; i++) dbf.readByte();

    char starter = (char) dbf.readByte();
    while (starter != 0x0D) {
      char[] title = new char[11];
      title[0] = (starter == 0) ? ' ' : (char) starter;
      for (int i = 1; i <= 10; i++) {
        char ch = (char) dbf.readByte();
        title[i] = (ch == 0) ? ' ' : ch;
      }
      char type = (char) dbf.readByte();
      Integer.reverseBytes(dbf.readInt());                       // int displ =
      int field_length = dbf.readByte();
      if (field_length < 0) field_length = 256 + field_length;
      dbf.readByte();                                            // byte dec_places =
      dbf.readByte();                                            // byte field_flags =
      Integer.reverseBytes(dbf.readInt());                       // int auto_incr_next =
      dbf.readByte();                                            // int auto_incr_step =
      dbf.readDouble();                                          // Skip 8 bytes
      field_names.add(new String(title));
      field_types.add(new Character(type));
      field_lengths.add(new Integer(field_length));
      starter = (char) dbf.readByte();
    }

    shp.readInt();                                               // int file_code = (0x0000270a = 9994 decimal)
    for (int i = 0; i < 5; i++) shp.readInt();                   // 5 unused bytes
    int file_size = 2 * shp.readInt();                           // File size in bytes (2 * 16-bit words)

    Integer.reverseBytes(shp.readInt());                         // int version = little-endian
    Integer.reverseBytes(shp.readInt()); // Shape type;          // int shape_type =
    Double.longBitsToDouble(Long.reverseBytes(shp.readLong()));  // double min_x
    Double.longBitsToDouble(Long.reverseBytes(shp.readLong()));  // double min_y =
    Double.longBitsToDouble(Long.reverseBytes(shp.readLong()));  // double max_x =
    Double.longBitsToDouble(Long.reverseBytes(shp.readLong()));  // double max_y =
    Double.longBitsToDouble(Long.reverseBytes(shp.readLong()));  // double min_z =
    Double.longBitsToDouble(Long.reverseBytes(shp.readLong()));  // double max_z =
    Double.longBitsToDouble(Long.reverseBytes(shp.readLong()));  // double min_m =
    Double.longBitsToDouble(Long.reverseBytes(shp.readLong()));  // double max_m =

    int file_pointer = 100;                                      // Header in DBF file is exactly 100 bytes.
    while (file_pointer < file_size) {
      dbf.readByte();                                            // byte del = ??
      String[] entry_names = new String[level + 1];
      String[] entry_nums = new String[level + 1];
      for (int j = 0; j < field_names.size(); j++) {
        char type = field_types.get(j).charValue();
        int length = field_lengths.get(j).intValue();

        if ((type == 'C') || (type == 'F') ||
            (type == 'N') || (type == 'D')) {
          byte[] string = new byte[length];
          for (int k = 0; k < length; k++) {
            byte ch = dbf.readByte();
            string[k] = ch;
          }

          String utf_string = new String(string, Charset.forName("UTF-8"));
          utf_string = utf_string.replace("\r", " ");
          utf_string = utf_string.replace("\n", " ");

          for (int k = 0; k <= level; k++) {
            if (field_names.get(j).trim().equals(name_field[k])) {
              entry_names[k] = utf_string.trim();
            }

            if (field_names.get(j).trim().equals(num_field[k])) {
              entry_nums[k] = utf_string.trim();
            }
          }
        }
      }

      ShapeUnit SU = new ShapeUnit();
      shp.readInt();                                               // int rec_no =
      int rec_length = shp.readInt() * 2;                          // rec_length in bytes, (not words)
      file_pointer += 8;

      // The actual shapes are coming up...

      int rec_pointer = 0;                                         // Pointer into variable length record

      while (rec_pointer < rec_length) {
        int rec_shape_type = Integer.reverseBytes(shp.readInt());
        rec_pointer += 4;
        file_pointer += 4;

        if (rec_shape_type == 5) {                             // Type 5 is a polygon. We expect to find:
                                                               //   The bounding box (4 doubles)
                                                               //   The number of separate polygons
                                                               //   The total number of points
                                                               //   Then for each part, the index of the first point
                                                               //   Then all the points. (x,y as doubles)


          Double.longBitsToDouble(Long.reverseBytes(shp.readLong()));   // double rec_min_x =
          Double.longBitsToDouble(Long.reverseBytes(shp.readLong()));   // double rec_min_y =
          Double.longBitsToDouble(Long.reverseBytes(shp.readLong()));   // double rec_max_x =
          Double.longBitsToDouble(Long.reverseBytes(shp.readLong()));   // double rec_max_y =

          rec_pointer += 32;
          file_pointer += 32;

          int no_parts = Integer.reverseBytes(shp.readInt());           // Number of separate "polygons"
          int no_points = Integer.reverseBytes(shp.readInt());          // Number of points

          rec_pointer += 8;
          file_pointer += 8;

          int[] part_start = new int[no_parts];
          for (int i = 0; i < no_parts; i++)
            part_start[i] = Integer.reverseBytes(shp.readInt());       // Indexes - first point.

          rec_pointer += (4 * no_parts);
          file_pointer += (4 * no_parts);

          for (int i = 0; i < no_parts; i++) {
            int no_points_in_part = 0;

            if (i < no_parts - 1)
              no_points_in_part = part_start[i + 1] - part_start[i];
            else
              no_points_in_part = no_points - part_start[i];

            DPolygon dpoly = new DPolygon();
            for (int j = 0; j < no_points_in_part; j++) {
              double p_x = Double.longBitsToDouble(Long.reverseBytes(shp.readLong()));
              double p_y = Double.longBitsToDouble(Long.reverseBytes(shp.readLong()));

              // Here we simplify squiggly shapes a bit; if the distance between subsequent points is
              // less than 1/2400 degrees (where our native resolution is 1/120), then ignore -
              // assume we didn't change rasterised cell.

              int p_x_int = (int) Math.round(2400 * p_x);
              int p_y_int = (int) Math.round(2400 * p_y);

              if (dpoly.npoints == 0) dpoly.addPoint(p_x_int, p_y_int);
              else if ((p_x_int != dpoly.xpoints[dpoly.npoints - 1]) ||
                       (p_y_int != dpoly.ypoints[dpoly.npoints - 1]))
                dpoly.addPoint(p_x_int, p_y_int);

              rec_pointer += 16;
              file_pointer += 16;
            }

            // Now determine if the polygon was reported clockwise or anti-clockwise. We'll need that later
            // to determine exclusions.

            double sum = ((360.0 + dpoly.xpoints[0]) - (360.0 + dpoly.xpoints[dpoly.npoints- 1])) *
                          (360.0 + dpoly.ypoints[0] + dpoly.ypoints[dpoly.npoints - 1]);

            for (int j = 1; j < dpoly.npoints; j++) {
              sum += ((360.0 + dpoly.xpoints[j]) - (360.0 + dpoly.xpoints[j - 1])) *
                      (360.0 + dpoly.ypoints[j] + dpoly.ypoints[j - 1]);
            }

            dpoly.clockwise = (sum > 0); // Normally would be other way round, but our
                                         // y-axis is inverted.

            SU.polygons.add(dpoly);
          }
          for (int i=0; i<= level; i++) {
            SU.metadata.put("ID_"+i, entry_nums[i]);
            SU.metadata.put("NAME_"+i,  entry_names[i]);
          }
          
          S.shapes.add(SU);

        } else if (rec_shape_type == 0) { // This is a null shape. It does occur, but contains no data.
                                        // Not clear to me what it is for, but handle/ignore it/

        } else System.out.println("Shape type " + rec_shape_type + " not implemented");
      }
    }

    dbf.close();
    shp.close();

    return S;
  }
}
