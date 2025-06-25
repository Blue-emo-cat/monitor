package org.example;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Scanner;

public class MonitorAndRestart {
    private static final String JMX_URL = "service:jmx:rmi:///jndi/rmi://localhost:1099/jmxrmi";
    private static final long MEMORY_THRESHOLD = 1000 * 1024 * 1024; // 定义内存使用阈值为120MB
    private static final long MEMORY_Warnning = 700 * 1024 * 1024; // 定义内存报警阈值为50MB
    private static long maxMemory;
    private static final Logger logger = LoggerFactory.getLogger(MonitorAndRestart.class);
    private static int port;
    private static final String TOMCAT_HOME = "F:\\tomcat"; // 请根据实际情况修改Tomcat路径

    public static void main(String[] args) {
        Scanner portid = new Scanner(System.in);
        System.out.print("请输入要监测的端口: ");
        port = portid.nextInt();
        printMemory();

        try {
            //建立jmx连接
            JMXServiceURL url = new JMXServiceURL(JMX_URL);
            JMXConnector jmxc = JMXConnectorFactory.connect(url, null);
            MBeanServerConnection mbsc = jmxc.getMBeanServerConnection();
            //获取堆内存 MBean
            ObjectName memoryMBean = new ObjectName("java.lang:type=Memory");
            CompositeData heapData = (CompositeData) mbsc.getAttribute(memoryMBean, "HeapMemoryUsage");
            // 3. 提取内存数据
            //long used = (Long) heapData.get("used");
            //long max = (Long) heapData.get("max");
            //long committed = (Long) heapData.get("committed");


            int pid = getPid(port);
            if (pid == -1) {
                logger.error("无法获取程序的PID。");
                return;
            }

            while (true) {
                // 3. 提取内存数据
                long used = (Long) heapData.get("used");
                long max = (Long) heapData.get("max");
                long committed = (Long) heapData.get("committed");
                double usePersent = (double) used / max * 100;

                String usep = String.format("%.2f", usePersent);
                double usePer = Double.parseDouble(usep);

                if (used == -1) {
                    logger.error("无法获取程序的内存使用情况。");
                    return;
                }

                logger.info("程序使用内存: {}MB", used / (1024 * 1024));
                logger.info("程序堆提交内存: {}%", committed);
                logger.info("程序堆内存使用率: {}%", usePer);

                if (used > MEMORY_Warnning) {
                    logger.warn("内存超过{}MB,请注意", (double) MEMORY_Warnning / 1024 / 1024);
                    logger.warn("程序使用内存: {}MB", used / (1024 * 1024));
                    logger.warn("程序堆提交内存: {}%", committed);
                    logger.warn("程序堆内存使用率: {}%", usePer);
                }

//                if (usedMemory > MEMORY_THRESHOLD) {
//                    logger.error("内存阈值超过{}MB,正在重启()...", (double) MEMORY_THRESHOLD / 1024 / 1024);
//                    logger.warn("程序使用内存: {}MB", usedMemory / (1024 * 1024));
//                    logger.warn("程序堆内存使用率: {}%", usePer);
//                    restartProgramA();
//                }

                Thread.sleep(3000);
            }
        } catch (Exception e) {
            logger.error("发生异常: ", e);
        }
    }

    private static void restartProgramA() {
        try {
            // 停止Tomcat
            //Runtime.getRuntime().exec(TOMCAT_HOME + "\\bin\\shutdown.bat");
            Runtime.getRuntime().exec("taskkill /F /IM " + getPid(port)); // 执行命令强制结束程序A
            Thread.sleep(10000); // 等待10秒确保Tomcat停止

            // 启动Tomcat
            Runtime.getRuntime().exec(TOMCAT_HOME + "\\bin\\startup.bat");
            Thread.sleep(10000); // 等待10秒确保Tomcat启动

            // 重新获取PID
            int newPid = getPid(port);
            if (newPid != -1) {
                logger.info("Tomcat重启后新的PID是: {}", newPid);
            } else {
                logger.error("重启后无法获取新的PID。");
            }
        } catch (Exception e) {
            logger.error("重启程序A时发生异常: ", e);
        }
    }

    public static int getPid(int port) {
        try {
            int poid = port;
            String pid = getPidByPort(poid);

            if (pid != null) {
                logger.info("运行在本地 {} 端口上的程序的 PID 是：{}", poid, pid);
                return Integer.parseInt(pid);
            } else {
                logger.info("没有找到运行在本地 {} 端口上的程序。", poid);
            }
        } catch (Exception e) {
            logger.error("获取PID时发生异常: ", e);
        }
        return -1;
    }

    public static String getPidByPort(int port) throws Exception {
        String[] cmd = {"cmd.exe", "/c", "netstat -ano | findstr :" + port};
        Process process = Runtime.getRuntime().exec(cmd);
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;
        String pid = null;
        while ((line = reader.readLine()) != null) {
            String[] parts = line.split("\\s+");
            if (parts.length > 0) {
                // 获取最后一列即为 PID
                pid = parts[parts.length - 1];
                break;
            }
        }
        reader.close();
        process.waitFor();
        return pid;
    }

    private static long getProcessMemoryUsage(int pid) throws Exception {
        String[] cmd = {"cmd.exe", "/c",
                "tasklist /FI \"PID eq " + pid + "\" /FO CSV /NH"};

        Process process = Runtime.getRuntime().exec(cmd);

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), "GBK"))) {
            String line;
            long memoryUsage = -1;

            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;

                String[] parts = line.split("\",\"");
                if (parts.length >= 5) {
                    String memoryStr = parts[parts.length - 1]
                            .replace("\"", "")
                            .replace(" K", "")
                            .replace(",", "");

                    try {
                        memoryUsage = Long.parseLong(memoryStr) * 1024;
                        break;
                    } catch (NumberFormatException e) {
                        // 转换失败保持-1
                    }
                }
            }

            return memoryUsage;
        }
    }

    public static void printMemory() {
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();

        long init = heapUsage.getInit();
        maxMemory = heapUsage.getMax();
        //long committed = heapUsage.getCommitted();

        logger.info("程序使用初始堆大小: {}MB", init / (1024 * 1024));
        logger.info("程序使用最大堆大小: {}MB", maxMemory / (1024 * 1024));
    }
}
