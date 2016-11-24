package model;

import java.util.Vector;

/**
 * @author Janon Wang
 */
public class CoreSwitch extends Node implements Switch{

    private int k;
    private Vector<Link> downLinks;

    public CoreSwitch(Address address, int k) {
        this.addr = address;
        this.k = k;
        this.downLinks = new Vector<>();     // 每个coreSwitch都有k条链路与k各pod相连
        for(int i=0; i<=k; i++) {  // 创建了k+1条链路，但是0号是不用的，为了序号从1开始
            this.downLinks.add(null);
        }
    }

    public int getK() {
        return this.k;
    }

    public void setDownLink(int i, Link l) {
        if (i > 0 && i <= k) {
            downLinks.set(i, l);
        }
    }

    public Link getDownLink(int i) {
        return downLinks.get(i);
    }

    public String getName() {
        return "Core" + addr.getCore();
    }
}
