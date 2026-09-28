package uz.dukeengine.client3d;

/**
 * The client's own canvas and raw-input plumbing, for a game's test to drive without a window: the frame its
 * painter is handed, drawing text with its own faces, and the listener that turns the window's input into what the
 * game's {@link CanvasInput} is offered.
 */
public final class RealCanvas {

    private RealCanvas() {
    }

    /** One frame of the client's canvas, the size of a window, its faces read off the classpath. */
    public static Canvas frame(int width, int height) {
        var text = new CanvasText(path -> RealCanvas.class.getResourceAsStream("/" + path));
        return new CanvasFrame(width, height, text, path -> new int[] {64, 64});
    }

    /** How many triangles a frame from {@link #frame} was drawn with. */
    public static int triangles(Canvas frame) {
        return ((CanvasFrame) frame).triangles().size();
    }

    /** The client's raw-input listener, offering the game what the window says, in a window {@code height} high. */
    public static com.jme3.input.RawInputListener inputs(CanvasInput game, int height) {
        return new CanvasInputs(game, () -> height);
    }
}
