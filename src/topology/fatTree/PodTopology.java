package topology.fatTree;

import main.VNFPSimulation;
import model.*;
import org.apache.commons.math3.geometry.spherical.twod.Edge;

import java.util.ArrayList;

/**
 * Created by Janon Wang on 2016/11/5.
 * all the method are package access only.
 * so only the FatTreeTopo can access it and all the interface will
 * be exposed in the FatTreeTopo class.
 * @author Janon Wang
 */
class PodTopology {
    private int k;
    private int podId;
    private PhysicalServer[][] physicalServers;  // PhysicalServer[edgeSwitchId][serverId]
    private AggreSwitch[] aggres;
    private EdgeSwitch[] edges;
    private Link[][] endLinks;  // Link[edgeSwitchId][serverId]
    private Link[][] aggreLinks;  // Link[aggrSwitchId][edgeSwitchId]
    private int[] vnfCount;  //int[vnfType]

    PodTopology(int k, int podId) {
        this.k = k;
        this.podId = podId;
        this.physicalServers = new PhysicalServer[k/2 + 1][k/2 + 1];
        this.aggres = new AggreSwitch[k/2 + 1];
        this.edges = new EdgeSwitch[k/2 + 1];
        this.endLinks = new Link[k/2 + 1][k/2 + 1];
        this.aggreLinks = new Link[k/2 + 1][k/2 + 1];
        // 初始化vnf的统计，全部置0
        vnfCount = new int[VNFPSimulation.vnfSum + 1];
        for (int i = 0; i < VNFPSimulation.vnfSum + 1; i++) {
            vnfCount[i] = 0;
        }
        // 初始化交换机
        for (int i = 1; i <= k/2; i++) {
            aggres[i] = new AggreSwitch(new Address(podId, i, 0, 0), k);
            edges[i] = new EdgeSwitch(new Address(podId, 0, i, 0), k);
        }
        // 初始化servers && 初始化links
        for (int i = 1; i <= k / 2; i++) {
            for (int j = 1; j <= k / 2; j++) {

                // 初始化server
                physicalServers[i][j] = new PhysicalServer(new Address(podId, 0, i, j));

                // 初始化edge links --- Link(downNode, upNode)
                // 外层循环遍历edgeSwitch， 内层循环遍历server
                endLinks[i][j] = new Link(physicalServers[i][j], edges[i]);
                physicalServers[i][j].setEdgeLink(endLinks[i][j]);
                edges[i].setDownLink(j, endLinks[i][j]);

                // 初始化aggregation links --- Link(downNode, upNode)
                // 外层循环遍历aggreSwitch， 内层循环遍历edgeSwitch
                aggreLinks[i][j] = new Link(edges[j], aggres[i]);
                edges[j].setUpLink(i, aggreLinks[i][j]);
                aggres[i].setDownLink(j, aggreLinks[i][j]);
            }
        }
    }

    int getK() {
        return this.k;
    }

    void setAggresUpLinks(int aggrei, int aggresLinki, Link link) {
        this.aggres[aggrei].setUpLink(aggresLinki, link);
    }

    int getPodId() {
        return podId;
    }

    void addVnf(int vnfType) {
        vnfCount[vnfType] ++;
    }

    int[] getVnfCounts() {
        return vnfCount;
    }

    int getVnfCount(int vnfType) {
        return vnfCount[vnfType];
    }

    EdgeSwitch getEdgeSwitch(int i) {
        return edges[i];
    }

    AggreSwitch getAggreSwitch(int i) {
        return aggres[i];
    }

    PhysicalServer[][] getServerInPod() {
        return this.physicalServers;
    }

    PhysicalServer[] getServerByEdge(short edgei) {
        return  this.physicalServers[edgei];
    }

    PhysicalServer getServerById(short edgei, short serveri) {
        return this.physicalServers[edgei][serveri];
    }

    PhysicalServer getServerByAddress(Address addr) {
        // 不用判断这个地址是否合理，在FatTreeTopo中已经判断过
            int edgei = addr.getEdge();
            int serveri = addr.getServer();
            return physicalServers[edgei][serveri];
    }

    // only for the aggregation switch and the edge switch in the pod
    Switch getSwitchByAddress(Address addr) {
        if(addr.isAggr()) {
            return aggres[addr.getAggr()];
        } else if(addr.isEdge()) {
            return edges[addr.getEdge()];
        } else {
            System.err.println("the address requested is not a switch address");
            return null;
        }
    }

    Link[][] getAllEndLinks() {
        return endLinks;
    }

    Link[] getEndLinksByEdges(int edgei) {
        return endLinks[edgei];
    }

    Link[][] getAllAggreLinks() {
        return aggreLinks;
    }

    Link[] getAggreLinksByAggres(int aggres) {
        return aggreLinks[aggres];
    }

    Iterable<Link> getRelatedLinks(Address addr) {
        ArrayList<Link> links = new ArrayList<>();
        if(addr.isServer()) {
            PhysicalServer s = getServerByAddress(addr);
            links.add(s.getEdgeLink());
            return links;
        } else if(addr.isEdge()) {
            EdgeSwitch s = (EdgeSwitch) getSwitchByAddress(addr);
            for(int i = 1; i <= s.getK()/2; i++) {
                links.add(s.getDownLink(i));
                links.add(s.getUpLink(i));
            }
            return links;
        } else {
            AggreSwitch s = (AggreSwitch) getSwitchByAddress(addr);
            for(int i = 1; i <= s.getK()/2; i++) {
                links.add(s.getDownLink(i));
                links.add(s.getUpLink(i));
            }
            return links;
        }
    }

    void verify() {
        for(int i = 1; i <= k/2; i++) {
            for(int j = 1; j <= k/2; j++) {
                PhysicalServer physicalServers = (PhysicalServer) endLinks[i][j].getDownNode();
                EdgeSwitch edgeSwitch = (EdgeSwitch) endLinks[i][j].getUpNode();
                if(physicalServers != this.physicalServers[i][j]) {
                    System.err.println("the server attached to the endLink is wrong");
                }
                if(edgeSwitch != this.edges[i]) {
                    System.err.println("the edge Switch attached to the endLink is wrong");
                }
                EdgeSwitch edgeSwitch1 = (EdgeSwitch) aggreLinks[i][j].getDownNode();
                AggreSwitch aggreSwitch = (AggreSwitch) aggreLinks[i][j].getUpNode();
                if(edgeSwitch1 != this.edges[j]) {
                    System.err.println("the edge Switch attached to the aggresLink is wrong");
                }
                if(aggreSwitch != this.aggres[i]) {
                    System.err.println("the aggre Switch attached to the aggresLink is wrong");
                }
            }
        }
    }

}
