package dfarium.blockmaps;

public class BlockEntry {
    public String id;
    public boolean needsSupport;
    public String introducedIn;

    public BlockEntry(String id, boolean needsSupport, String introducedIn) {
        this.id = id;
        this.needsSupport = needsSupport;
        this.introducedIn = introducedIn;
    }
}
