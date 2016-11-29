package main;

import algorithms.AdvancedVNFPlacement;
import algorithms.HeuristicVNFPlacement;
import algorithms.VNFPlacement;
import manager.NetworkServiceManager;
import model.*;
import topology.Topology;
import topology.fatTree.FatTreeTopo;

import java.io.File;
import java.io.FileWriter;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Set;


/**
 * Created by Janon Wang on 2016/11/4.
 * 仿真程序的入口，初始化拓扑和NS产生器。两部分的算法都是对这边产生的server，vm，vnf进行操作
 * @author Janon Wang
 */
public class VNFPSimulation {
    public final static int vnfSum = 20; // the total type of the VNF
    // private final static double vnfRelationPara = 0.1;

    private final static double alpha = 2.1;  // positive number!
    private final static double trafficRateMin = 10; // Mbps

    private final static int fatTreeK = 10;

    private Set<NetworkService> acceptedNS;
    private VirtualMachine[] availableVNF; // 用于记录对于每种vnf，目前空闲的VM信息，某一时刻，对于某种VNF，只有一个可行的VNF, VNF的类型即为其数组序号
    private Topology topo;
    private VNFPlacement vnfPlacement;

    private VNFPSimulation(Topology topo, VNFPlacement vnfPlacement) {
        this.acceptedNS = new HashSet<>();
        this.availableVNF = new VirtualMachine[vnfSum + 1];
        this.topo = topo;
        this.vnfPlacement = vnfPlacement;
    }

    /**
     * 需要的数据：
     * 1、统计接受的NS的数量
     * 2、接受的NS的平均跳数
     * 3、接受的服务链的平均长度，与平均跳数对比
     * 4、vnfCountMatrix统计的结果
     * 5、每个Pod中的各类VNF统计
     * 6、每个EdgeSwitch上的各类VNF统计
     * 7、每个server上的各类VNF的统计
     * 8、每个server资源使用情况统计
     */
    private void showSimulationResult1(double para1, double para2, double para3) {
        String filename = "SimulationResult" + "-" + para1 + "-" + para2 + "-" + para3 + ".txt";
        File file = new File(filename);
        if(file.exists()) {
            if(!file.delete())
                System.err.println("the result file delete fail");
        } else {
            try{
                if(!file.createNewFile())
                    System.err.println("the result file create fail");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        try{
            FileWriter out = new FileWriter(file);
            out.write("-------------仿真结果----------- \r\n");
            double acceptNSCount = this.acceptedNS.size();

            out.write("接受的network service总数为：" + acceptNSCount + "\r\n");

            double sfcTotalLength = 0;
            double nsTotalHops = 0;
            for(NetworkService s : this.acceptedNS) {
                sfcTotalLength += s.sfcLength;
                int nsHops = 0;
                LinkedList<VirtualNetworkFunction> vnfs = s.sfcList;
                Iterator<VirtualNetworkFunction> vnfsIterator = vnfs.iterator();
                Address lastNode = vnfsIterator.next().position;
                while(vnfsIterator.hasNext()) {
                    Address thisNode = vnfsIterator.next().position;
                    nsHops += distanceBetweenTwoServer(lastNode, thisNode);
                    lastNode = thisNode;
                }
                nsTotalHops += nsHops;
            }
            double sfcAverLength = sfcTotalLength / acceptNSCount;
            double nsAverHops = nsTotalHops / acceptNSCount;

            out.write("接受的network service中服务链的平均长度为：" + sfcAverLength + "\r\n");

            out.write("接受的network service中的平均的跳数为：" + nsAverHops + "\r\n");

            out.write("vnf count matrix 统计的结果如下：");
            int[][] vnfCountMatrix = vnfPlacement.getVnfCountMatrix();
            for(int i = 1; i < VNFPSimulation.vnfSum + 1; i++) {
                out.write("\r\n     类型为 ***" + i + "*** 的VNF与其他之间的关联性：\r\n         ");
                for (int j = 1; j < VNFPSimulation.vnfSum + 1; j++) {
                    out.write("vnf" + j + ": " + vnfCountMatrix[i][j] + "    ");
                }
            }

            out.write("\r\n\r\n\r\n\r\n\r\n\r\n每个pod中不同类型VNF的统计：");
            for(int i = 1; i <= fatTreeK; i++) {
                out.write("\r\n\r\n\r\n\r\n     pod " + i + "中的统计情况：\r\n         ");
                int[] podVnf = topo.getPodVnfCounts(i);
                for(int j = 1; j <= VNFPSimulation.vnfSum; j++) {
                    out.write("vnf" + j + "的总数为: " + podVnf[j] + "    ");
                }
                out.write("\r\n\r\n         pod中每个EdgeSwitch上不同的VNF的统计：");
                for(int j = 1; j <= fatTreeK / 2; j++) {
                    out.write("\r\n             EdgeSwitch" + j + "中的统计情况：\r\n                 ");
                    int[] edgeVnf = topo.getEdgeVnfCounts(i, j);
                    for(int k = 1; k <= VNFPSimulation.vnfSum; k++) {
                        out.write("vnf" + k + "的总数为: " + edgeVnf[k] + "    ");
                    }
                }
            }

            out.write("\r\n\r\n\r\nServer资源使用的情况统计及启动的VNF统计：\r\n");
            for(int i = 1; i <= fatTreeK; i++) {
                for(int j = 1; j <= fatTreeK / 2; j++) {
                    for(int k = 1; k <= fatTreeK / 2; k++) {
                        PhysicalServer server = topo.getServerById(i, j, k);
                        out.write("\r\n\r\n" + server.getAddress().toString() + ":\r\n");
                        out.write("     资源剩余： " + "cpu:" + server.getCpuCoreRemain() + "    ram:" + server.getRamRemain() + "\r\n");
                        out.write("     不同的VNF的统计： \r\n         ");
                        int[] serverVnf = server.getVnfCounts();
                        for(int h = 1; h <= VNFPSimulation.vnfSum; h++) {
                            out.write("vnf" + h + "的总数为: " + serverVnf[h] + "    ");
                        }
                    }
                }
            }
            out.close();
        } catch(Exception e1) {
            e1.printStackTrace();
        }

    }

    private void showSimulationResult2(double para1, double para2, double para3, double vnfRelationPara) {
        String filename = "AdvancedSimulationResult-summarize.txt";
        File file = new File(filename);

        if(para1 == 1 && para2 == 0 && para3 == 0 && vnfRelationPara == 0.3) {
            try{
                if(file.exists()) {
                    if(!file.delete())
                        System.err.println("the result file delete fail");
                }
                if(!file.createNewFile())
                    System.err.println("the result file create fail");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        try{
            FileWriter out = new FileWriter(file, true);
            out.write("-------------仿真结果----------- \r\n" +
                    "当前的仿真条件为：\r\n" +
                    "fatTreeK: " + fatTreeK  + "    vnfRelationPara: " + vnfRelationPara + "    权值系数为: " + para1 + "  " + para2 + "  " + para3 + "\r\n" +
                    "--------------------------------\r\n");

            double acceptNSCount = this.acceptedNS.size();

            out.write("接受的network service总数为：" + acceptNSCount + "\r\n");

            double sfcTotalLength = 0;
            double nsTotalHops = 0;
            for(NetworkService s : this.acceptedNS) {
                sfcTotalLength += s.sfcLength;
                int nsHops = 0;
                LinkedList<VirtualNetworkFunction> vnfs = s.sfcList;
                Iterator<VirtualNetworkFunction> vnfsIterator = vnfs.iterator();
                Address lastNode = vnfsIterator.next().position;
                while(vnfsIterator.hasNext()) {
                    Address thisNode = vnfsIterator.next().position;
                    nsHops += distanceBetweenTwoServer(lastNode, thisNode);
                    lastNode = thisNode;
                }
                nsTotalHops += nsHops;
            }
            double sfcAverLength = sfcTotalLength / acceptNSCount;
            double nsAverHops = nsTotalHops / acceptNSCount;
            double averHopsBetweenTwoVnf = nsAverHops / (sfcAverLength - 1);

            out.write("接受的network service中服务链的平均长度为：" + sfcAverLength + "\r\n" +
                      "接受的network service中的平均的跳数为：" + nsAverHops + "\r\n");
            out.write("两个vnf之间的平均跳数：" + averHopsBetweenTwoVnf + "\r\n\r\n\r\n");
            out.close();
        } catch(Exception e1) {
            e1.printStackTrace();
        }
    }

    private int distanceBetweenTwoServer(Address addr1, Address addr2) {
        boolean isSamePod = addr1.getPod() == addr2.getPod();
        boolean isSameEdge = addr1.getEdge() == addr2.getEdge();
        boolean isSameServer = addr1.getServer() == addr2.getServer();
        if(isSamePod) {
            if(isSameEdge) {
                if(isSameServer) {
                    return 0;
                } else {
                    return 1;
                }
            } else {
                return 2;
            }
        } else {
            return 3;
        }
    }




    private static void runOnce(double para1, double para2, double para3, double vnfRelationPara) {
        System.out.print("-----仿真程序开始-----\n");
        System.out.println("权值系数为：" + para1 + "   " + para2 + "   " + para3 + "   " + "vnfRelation系数为：" + vnfRelationPara);
        FatTreeTopo topo = new FatTreeTopo(fatTreeK);
        NetworkServiceManager networkServiceManager = new NetworkServiceManager(vnfSum, vnfRelationPara,
                alpha, trafficRateMin);
        VNFPlacement vnfPlacement = new AdvancedVNFPlacement(topo, para1, para2, para3);
        VNFPSimulation vnfpSimulation = new VNFPSimulation(topo, vnfPlacement);
        boolean ifContinue = true;
        while(ifContinue) {
            NetworkService ns = networkServiceManager.nextNS();
//            System.out.print("产生的服务链为：");
//            for(VirtualNetworkFunction v : ns.sfcList) {
//                System.out.print(v.vnfType + "-->");
//            }
//            System.out.print("end\n");
            vnfPlacement.countVnf(ns);
            // vnfPlacement.showVnfCountMatrix();
            for(VirtualNetworkFunction v : ns.sfcList) {
                if(vnfpSimulation.availableVNF[v.vnfType] == null) {
                    VirtualMachine vm = vnfPlacement.placeNewVNF(v.vnfType);
                    if(vm == null) {
                        ifContinue = false;
                        break;
                    }
                    vnfpSimulation.availableVNF[v.vnfType] = vm;
                    if(vm.isResEnough(v.mipsNeeded)){
                        vm.launchVnf(v);
                    } else {
                        System.err.println("1随机产生的ns的traffic rate 太大了，一个VM都无法容纳");
                    }
                } else {
                    if(vnfpSimulation.availableVNF[v.vnfType].isResEnough(v.mipsNeeded)) {
                        VirtualMachine availableVM = vnfpSimulation.availableVNF[v.vnfType];
                        availableVM.launchVnf(v);
                        v.position = availableVM.position;
                    } else {
                        VirtualMachine vm = vnfPlacement.placeNewVNF(v.vnfType);
                        if(vm == null) {
                            ifContinue = false;
                            break;
                        }
                        vnfpSimulation.availableVNF[v.vnfType] = vm;
                        if(vm.isResEnough(v.mipsNeeded)){
                            vm.launchVnf(v);
                        } else {
                            System.err.println("2随机产生的ns的traffic rate 太大了，一个VM都无法容纳");
                        }
                    }
                }
            }
            if(ifContinue)
                vnfpSimulation.acceptedNS.add(ns);
        }
        System.out.print("-----打印数据-----\n\n\n");
        vnfpSimulation.showSimulationResult2(para1, para2, para3, vnfRelationPara);
        // vnfpSimulation.showSimulationResult1(para1, para2, para3);
    }


    public static void main(String[] args) {

        double vnfRelationPara = (double)3 / 10;

        double para1 = 1;
        double para2 = 0;
        double para3 = 0;
        VNFPSimulation.runOnce(para1, para2, para3, vnfRelationPara);

        para3 = 0.5;
        VNFPSimulation.runOnce(para1, para2, para3, vnfRelationPara);

        para3 = 1;
        VNFPSimulation.runOnce(para1, para2, para3, vnfRelationPara);

        para3 = 1.5;
        VNFPSimulation.runOnce(para1, para2, para3, vnfRelationPara);

        para1 = 0;
        para3 = 1;
        VNFPSimulation.runOnce(para1, para2, para3, vnfRelationPara);
    }
}
