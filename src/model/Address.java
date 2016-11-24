package model;

/**
 * @author Janon Wang
 */
public class Address {
    private long addr;
    private static long MASK = 0x000000000000ffffL;

    public Address(long addr) {
        this.addr = addr;
    }

    public Address(short pod, short aggr, short edge, short server) {
        this.addr |= (pod & MASK);
        addr <<= 16;
        this.addr |= (aggr & MASK);
        addr <<= 16;
        this.addr |= (edge & MASK);
        addr <<= 16;
        this.addr |= (server & MASK);
        check();
    }

    public Address(int pod, int aggr, int edge, int server) {
        this((short) pod, (short) aggr, (short) edge, (short) server);
    }

    public long getAddress() {
        return addr;
    }

    public short getPod() {
        return (short) ((addr >> 48) & MASK);
    }
    // coreSwitch的地址和pod的地址用了同一个字段
    public short getCore() {
        return (short) ((addr >> 48) & MASK);
    }

    public short getAggr() {
        return (short) ((addr >> 32) & MASK);
    }

    public short getEdge() {
        return (short) ((addr >> 16) & MASK);
    }

    public short getServer() {
        return (short) ((addr) & MASK);
    }

    public boolean isServer() {
        return getServer() != 0 && getEdge() != 0 && getAggr() == 0 && getPod() != 0; // host != 0, aggr == 0, edge != 0, pod != 0
    }

    public boolean isEdge() {
        return getServer() == 0 && getEdge() != 0 && getAggr() == 0 && getPod() != 0; // host == 0, aggr == 0, edge != 0, pod != 0
    }

    public boolean isAggr() {
        return getServer() == 0 && getEdge() == 0 && getAggr() != 0 && getPod() != 0; // host == 0, aggr != 0, edge == 0, pod != 0
    }

    public boolean isCore() {
        return getServer() == 0 && getEdge() == 0 && getAggr() == 0 && getCore() != 0; // host == 0, aggr == 0, edge == 0, pod != 0
    }

    public String toString() {
        return "P" + this.getPod() + "A" + this.getAggr() + "E" + this.getEdge() + "H" + this.getServer();
    }

    private boolean isValid() {
        return this.isCore() || this.isAggr() || this.isEdge() || this.isServer();
    }

    private void check() {
        if (!isValid()) {
            System.err.println("You create an invalid address!");
            System.exit(0);
        }
    }

    public boolean equals(Object o) {
        if (o == null || ! (o instanceof Address)) {
            return false;
        }
        Address another = (Address) o;
        return addr == another.addr;
    }
}
