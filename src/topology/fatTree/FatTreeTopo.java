package topology.fatTree;

import model.*;
import topology.Topology;

import java.util.ArrayList;

/**
 * @author Janon Wang
 */
public class FatTreeTopo implements Topology{

    private int k;
    private CoreSwitch[] coreSwitches;
    private PodTopology[] podTopologies;
    private Link[][] coreLinks;  // coreLink[coreSwitch][pod]

    public FatTreeTopo(int k) {
        this.k = k;
        this.coreSwitches = new CoreSwitch[k*k/4 + 1]; // 一共（k * k / 4）台核心层交换机
        this.podTopologies = new PodTopology[k + 1]; // 一共（k）个pod
        // 初始化podTopo
        for(int i = 1; i <= k; i++) {
            this.podTopologies[i] = new PodTopology(k, i);
        }
        // 初始化core switch
        // core交换机的地址用了pod的地址段
        for(int i = 1; i <= k*k/4; i++) {
            this.coreSwitches[i] = new CoreSwitch(new Address(i, 0, 0, 0), k);
        }
        // 初始化coreLinks
        /*
         * [coreSwitch][pod] 每个coreswitch和每个pod之间都会有一条连线，
         * 连在pod上的哪台aggre switch在此链路坐标上不能直接读出，
         * 但是能计算出来,pod中的第{A mod（k/2}个aggre switch，其中A的取值为Link[A][]中的A
         */
        this.coreLinks = new Link[k*k/4 + 1][k + 1]; // coreLink[coreSwitch][pod]
        /*
         * 将core switch分为k/2组，每组k/2个。
         * i代表第i组core switch， j代表组内的第j个core switch， h表示第h个pod
         */
        for(int i= 1; i <= k/2; i++) {
            for(int j = 1; j <= k/2; j++){
                for(int h = 1; h <= k; h++) {
                    this.coreLinks[(i - 1)*k/2 + j][h] = new Link(this.podTopologies[h].getAggreSwitch(i),
                            this.coreSwitches[(i - 1)*k/2 + j]);
                    this.coreSwitches[(i - 1)*k/2 + j].setDownLink(h, this.coreLinks[(i - 1)*k/2 + j][h]);
                    /*
                     * 由于第i组中的core switch都与pod内的第i个aggres相连，
                     * 所以i是第h个pod中的第i个aggres switch, j是指这是这个aggres switch的第j条链路
                     */
                    this.podTopologies[h].setAggresUpLinks(i, j, this.coreLinks[(i - 1)*k/2 + j][h]);
                }
            }
        }
    }


    public int getK() {
        return this.k;
    }

    @Override
    public void launchVM(PhysicalServer server, VirtualMachine vm) {
        Address address = server.getAddress();
        int podId = address.getPod();
        int edgeId = address.getEdge();
        PodTopology pod = this.getPod(podId);
        pod.addVnf(vm.vnfType);
        pod.getEdgeSwitch(edgeId).addVnf(vm.vnfType);
        server.launchVM(vm);
        vm.position = address;
    }

    @Override
    public PhysicalServer[] getServerByEdge(short podId, short edgei) {
        return this.podTopologies[podId].getServerByEdge(edgei);
    }

    @Override
    public PhysicalServer getServerById(int podId, int edgei, int serveri) {
        return this.podTopologies[podId].getServerById((short)edgei, (short)serveri);
    }

    @Override
    public PhysicalServer getServerByAddress(Address addr) {
        if(addr.isServer()) {
            return this.podTopologies[addr.getPod()].getServerByAddress(addr);
        } else {
            System.err.println("the address requested is not a server address");
            return null;
        }
    }

    @Override
    public Switch getSwitchByAddress(Address addr) {
        if(addr.isCore()) {
            return this.coreSwitches[addr.getCore()];
        } else {
            return this.podTopologies[addr.getPod()].getSwitchByAddress(addr);
        }
    }

    @Override
    public Iterable<Link> getRelatedLinks(Address addr) {
        ArrayList<Link> links = new ArrayList<>();
        if(addr.isCore()) {
            CoreSwitch s = (CoreSwitch) this.getSwitchByAddress(addr);
            for(int i = 1; i <= s.getK(); i++) {
                links.add(s.getDownLink(i));
            }
            return links;
        } else {
            return podTopologies[addr.getPod()].getRelatedLinks(addr);
        }
    }

    @Override
    public int[] getPodVnfCounts(int podId) {
        return this.getPod(podId).getVnfCounts();
    }

    @Override
    public int[] getEdgeVnfCounts(int podId, int edgeId) {
        return this.getPod(podId).getEdgeSwitch(edgeId).getVnfCounts();
    }

    private PodTopology getPod(int podId) {
        return this.podTopologies[podId];
    }

    public static void verify(int k) {
        FatTreeTopo topology = new FatTreeTopo(k);
        System.out.println("-------------Verifying-------------");
        for(int m = 1; m <= k / 2; m++) {
            for(int n = 1; n <= k / 2; n++) {
                for(int i = 1; i <= k; i++) {
                    CoreSwitch coreSwitch = (CoreSwitch) topology.coreLinks[(m - 1)*k/2 + n][i].getUpNode();
                    AggreSwitch aggreSwitch = (AggreSwitch) topology.coreLinks[(m - 1)*k/2 + n][i].getDownNode();
                    if(coreSwitch != topology.coreSwitches[(m - 1)*k/2 + n]) {
                        System.err.println("the core Switch attached to the coreLink[" + ((m - 1)*k/2 + n) + "][" + i + "] is wrong");
                    }
                    if(aggreSwitch != topology.podTopologies[i].getAggreSwitch(m)){
                        System.err.println("the aggre Switch attached to the coreLink[" + ((m - 1)*k/2 + n) + "][" + i + "] is wrong");
                    }
                }
            }
        }
        System.out.println("the core links is checked");
        for(int i = 1; i <= k; i++) {
            topology.getPod(i).verify();
            System.out.println("pod " + i + " is verified");
        }
        System.out.println("-------------Verify Work Completed-------------");
    }

}
