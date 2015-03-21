package info.persistent.react.jscomp;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Test {@link React}.
 */
public class ReactTest {
  @Test public void testIsReactSourceName() {
    assertTrue(React.isReactSourceName("/src/react.js"));
    assertFalse(React.isReactSourceName("/src/notreact.js"));
    assertTrue(React.isReactSourceName("/src/react.min.js"));
    assertFalse(React.isReactSourceName("/src/react.max.js"));
    assertTrue(React.isReactSourceName("/src/react-with-addons.js"));
  }
}
