package model;

/**
 * @author Janon Wang
 */
// server, switch,等都需要继承这个接口
public abstract class Node {
    protected Address addr;

    public long getLongAddress() {
        return addr.getAddress();
    }

    public Address getAddress() {
        return addr;
    }

    public String getName() {
        return addr.toString();
    }
}
