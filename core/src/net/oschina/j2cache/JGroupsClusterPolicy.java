package net.oschina.j2cache;

import org.jgroups.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.net.URL;

/**
 * 使用 JGroups 组播进行集群内节点通讯
 * @author winterlau
 */
public class JGroupsClusterPolicy extends ReceiverAdapter implements ClusterPolicy {

    private final static String CONFIG_XML = "/network.xml";
    private final static Logger log = LoggerFactory.getLogger(JGroupsClusterPolicy.class);

    private JChannel channel;
    private String name;

    public JGroupsClusterPolicy(String name) {
        this.name = name;
    }

    @Override
    public void connect() {
        try{
            long ct = System.currentTimeMillis();

            URL xml = CacheChannel.class.getResource(CONFIG_XML);
            if(xml == null)
                xml = getClass().getClassLoader().getParent().getResource(CONFIG_XML);
            channel = new JChannel(xml);
            channel.setReceiver(this);
            channel.connect(name);

            log.info("Connected to jgroups channel:" + name + ", time " + (System.currentTimeMillis()-ct) + " ms.");

        }catch(Exception e){
            throw new CacheException(e);
        }
    }

    @Override
    public void disconnect() {
        channel.close();
    }

    @Override
    public void receive(Message msg) {
        //无效消息
        byte[] buffers = msg.getBuffer();
        if(buffers.length < 1){
            log.warn("Message is empty.");
            return;
        }

        //不处理发送给自己的消息
        if(msg.getSrc().equals(channel.getAddress()))
            return ;

        try{
            Command cmd = Command.parse(buffers);

            if(cmd == null)
                return;

            switch(cmd.getOperator()){
                case Command.OPT_DELETE_KEY:
                    this.evict(cmd.getRegion(), cmd.getKey());
                    break;
                case Command.OPT_CLEAR_KEY:
                    this.clear(cmd.getRegion());
                    break;
                default:
                    log.warn("Unknown message type = " + cmd.getOperator());
            }
        }catch(Exception e){
            log.error("Failed to handle received msg" , e);
        }
    }

    /**
     * 发送清除缓存的广播命令
     *
     * @param region : Cache region name
     * @param key    : cache key
     */
    @Override
    public void sendEvictCmd(String region, Serializable key) {
        Command cmd = new Command(Command.OPT_DELETE_KEY, region, key);
        try {
            Message msg = new Message(null, null, cmd.toBuffers());
            channel.send(msg);
        } catch (Exception e) {
            log.error("Failed to delete cache,region="+region+",key="+key, e);
        }
    }

    /**
     * 发送清除缓存的广播命令
     *
     * @param region: Cache region name
     */
    @Override
    public void sendClearCmd(String region) {
        Command cmd = new Command(Command.OPT_CLEAR_KEY, region, "");
        try {
            Message msg = new Message(null, null, cmd.toBuffers());
            channel.send(msg);
        } catch (Exception e) {
            log.error("Failed to clear cache,region="+region, e);
        }
    }

    @Override
    public void viewAccepted(View view) {
        log.info(String.format("Group Members Changed, LIST: %s",
                String.join(",", view.getMembers().stream().map(a -> a.toString()).toArray(String[]::new)))
        );
    }

}
