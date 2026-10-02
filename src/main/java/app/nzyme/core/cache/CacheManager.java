package app.nzyme.core.cache;

import app.nzyme.core.NzymeNode;
import app.nzyme.plugin.distributed.messaging.MessageHandler;
import app.nzyme.plugin.distributed.messaging.MessageProcessingResult;
import app.nzyme.plugin.distributed.messaging.MessageType;
import app.nzyme.plugin.distributed.messaging.ReceivedMessage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CacheManager {

    private static final Logger LOG = LogManager.getLogger(CacheManager.class);

    private final NzymeNode nzyme;

    public CacheManager(NzymeNode nzyme) {
        this.nzyme = nzyme;
    }

    public void initialize() {
        nzyme.getMessageBus().onMessageReceived(MessageType.INVALIDATE_CACHE, new MessageHandler() {
            @Override
            public MessageProcessingResult handle(ReceivedMessage message) {
                try {
                    String cacheType = (String) message.parametersMap().get("cache_type");
                    if (cacheType == null) {
                        LOG.error("Could not handle [{}] message: Unexpected payload.",
                                MessageType.INVALIDATE_CACHE);
                        return MessageProcessingResult.FAILURE;
                    }

                    switch (cacheType) {
                        case "context_macs":
                            LOG.info("Invalidating MAC address context cache on request of node [{}].",
                                    message.sender());
                            nzyme.getContextService().invalidateMacAddressCache();
                            break;
                        case "context_ips":
                            LOG.info("Invalidating IP address context cache on request of node [{}].",
                                    message.sender());
                            nzyme.getContextService().invalidateIpAddressCache();
                            break;
                        case "environment_data":
                            LOG.info("Invalidating environment data cache on request of node [{}].",
                                    message.sender());
                            nzyme.getEnvironmentService().initialize();
                            break;
                        default:
                            LOG.error("Could not handle [{}] message: Unknown cache type.",
                                    MessageType.INVALIDATE_CACHE);
                            return MessageProcessingResult.FAILURE;
                    }
                } catch(Exception e) {
                    LOG.error("Could not handle [{}] message.", MessageType.INVALIDATE_CACHE, e);
                    return MessageProcessingResult.FAILURE;
                }

                return MessageProcessingResult.SUCCESS;
            }

            @Override
            public String getName() {
                return "CacheManager";
            }
        });
    }

}
