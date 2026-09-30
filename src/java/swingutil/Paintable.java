package swingutil;

import java.awt.Graphics;

/** Public dispatch to the paintComponent method exposed by a Clojure proxy.
 * Lets proxy-call-with-super invoke the original Swing implementation on Java 8
 * without reflective access to a protected method or constructing a new UI.
 */
public interface Paintable {
    void paintComponent(Graphics graphics);
}
