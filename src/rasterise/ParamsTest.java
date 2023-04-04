package rasterise;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.Assert;
import org.junit.jupiter.api.Test;

class ParamsTest {

  @Test
  void testGetName() {
    Params P = new Params("Potato");
    Assert.assertTrue(P.getName().equals("Potato"));
  }

}
