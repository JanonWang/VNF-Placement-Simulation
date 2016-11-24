package model;

/**
 * @author Janon Wang
 */
public class Link {

    private final int bandwidth;
    private final int delay;
    private final Node up;
    private final Node down;
    private int bwUsed;
    private int bwRemain;

    public Link(Node down, Node up) {
        this.down = down;
        this.up = up;
        this.bandwidth = 1;
        this.delay = 1;
        this.bwUsed = 0;
        this.bwRemain = this.bandwidth - this.bwUsed;
    }

    public Link(Node down, Node up,int bw, int delay) {
        this.down = down;
        this.up = up;
        this.bandwidth = bw;
        this.delay = delay;
        this.bwUsed = 0;
        this.bwRemain = this.bandwidth - this.bwUsed;
    }

    public boolean isBwEnough(int bw) {
        return bw < bwRemain;
    }

    public boolean consumeBw(int bw) {
        if(bw < bwRemain) {
            bwUsed += bw;
            bwRemain -= bw;
            return true;
        } else {
            return false;
        }
    }

    public int getBw() {
        return this.bandwidth;
    }

    public int getDelay() {
        return this.delay;
    }

    public Node getUpNode() {
        return this.up;
    }

    public Node getDownNode() {
        return this.down;
    }
}
