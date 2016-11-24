package model;

import main.VNFPSimulation;

import java.util.*;

/**
 * @author Janon Wang
 */
public class EdgeSwitch extends Node implements Switch{

    private int k;
    private Vector<Link> upports;
    private Vector<Link> downports;
    private int[] vnfCount;

    public EdgeSwitch(Address address, int k) {
        this.addr = address;
        this.k = k;
        this.upports = new Vector<>();
        this.downports = new Vector<>();
        this.vnfCount = new int[VNFPSimulation.vnfSum + 1];  // 序号0不用，从1开始，保持一致
        // 接入层交换机与k/2个汇聚成交换机相连， 与k/2个服务器相连
        for (int i = 0; i <= k / 2; i++) {
            this.upports.add(null);
            this.downports.add(null);
        }
        // 初始化vnf的统计，全部置0
        for (int i = 0; i < VNFPSimulation.vnfSum + 1; i++) {
            vnfCount[i] = 0;
        }
    }

    public int getK() {
        return this.k;
    }

    public void setUpLink(int i, Link l) {
        if (i > 0 && i <= k / 2) {
            this.upports.set(i, l);
        }
    }

    public Link getUpLink(int i) {
        return  this.upports.get(i);
    }

    public void setDownLink(int i, Link l) {
        if (i > 0 && i <= k/2) {
            this.downports.set(i, l);
        }
    }

    public Link getDownLink(int i) {
        return  this.downports.get(i);
    }

    public Vector<Link> getDownLinks() {
        return this.downports;
    }

    public Vector<Link> getUpLinks() {
        return this.upports;
    }

    public void addVnf(int vnfType) {
        vnfCount[vnfType] ++;
    }

    public int[] getVnfCounts() {
        return vnfCount;
    }

    public int getVnfCount(int vnfType) {
        return vnfCount[vnfType];
    }

    public String getName() {
        return "EdgeSwitch" + addr.getEdge();
    }
}
