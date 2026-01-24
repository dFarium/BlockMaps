package dfarium.blockmaps;

public class BlockEntry {
    public String id;
    public boolean needsSupport;

    public BlockEntry(String id, boolean needsSupport) {
        this.id = id;
        this.needsSupport = needsSupport;
    }
}
