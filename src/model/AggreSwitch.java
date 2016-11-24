package model;

import java.util.Vector;

/**
 * @author Janon Wang
 */
public class AggreSwitch extends Node implements Switch{

    private int k;
    private Vector<Link> upLinks;
    private Vector<Link> downLinks;


    public AggreSwitch(Address address, int k) {
        this.addr = address;
        this.k = k;
        this.upLinks = new Vector<>();  // 汇聚层交换机与核心层有k/2条链路
        this.downLinks = new Vector<>(); // 汇聚层与接入层有k/2条链路
        for(int i=0; i<=k/2; i++) {  // 创建了k/2+1条链路，但是0号是不用的，为了序号从1开始
            this.upLinks.add(null);
            this.downLinks.add(null);
        }
    }

    public int getK() {
        return this.k;
    }

    public void setUpLink(int i, Link l) {
        if (i > 0 && i <= k / 2) {
            this.upLinks.set(i, l);
        }
    }

    public Link getUpLink(int i) {
        return  this.upLinks.get(i);
    }

    public void setDownLink(int i, Link l) {
        if (i > 0 && i <= k/2) {
            this.downLinks.set(i, l);
        }
    }

    public Link getDownLink(int i) {
        return  this.downLinks.get(i);
    }

    public String getName() {
        return "Aggre" + addr.getAggr();
    }
}
