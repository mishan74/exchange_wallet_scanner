package io.lastwill.eventscan.services.monitors;

import io.lastwill.eventscan.events.model.UserPaymentEvent;
import io.lastwill.eventscan.model.CryptoCurrency;
import io.lastwill.eventscan.model.NetworkType;
import io.lastwill.eventscan.repositories.ExchangRequestRepository;
import io.mywish.blockchain.WrapperOutput;
import io.mywish.blockchain.WrapperTransaction;
import io.mywish.scanner.model.NewBlockEvent;
import io.mywish.scanner.services.EventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;


@Slf4j
@Component
public class BtcPaymentMonitor {
    @Autowired
    private ExchangRequestRepository exchangRequestRepository;
    @Autowired
    private EventPublisher eventPublisher;

    @EventListener
    private void handleBtcBlock(NewBlockEvent event) {

        if (event.getNetworkType() != NetworkType.BTC_MAINNET) {
            return;
        }
        Set<String> addresses = event.getTransactionsByAddress().keySet();
        if (addresses.isEmpty()) {
            return;
        }
        exchangRequestRepository.findByBtcRxAddress(addresses)
                .forEach(exchangeDetails -> {
                    List<WrapperTransaction> txes = event.getTransactionsByAddress().get(exchangeDetails.getBtcAddress());
                    if (txes == null || txes.isEmpty()) {
                        return;
                    }

                    for (WrapperTransaction tx : txes) {
                        for (WrapperOutput output : tx.getOutputs()) {
                            if (output.getParentTransaction() == null) {
                                log.warn("Skip it. Output {} has not parent transaction.", output);
                                continue;
                            }
                            if (!output.getAddress().equalsIgnoreCase(exchangeDetails.getBtcAddress())) {
                                continue;
                            }
                            eventPublisher.publish(
                                    new UserPaymentEvent(
                                            exchangeDetails.getId(),
                                            exchangeDetails.getBtcAddress(),
                                            NetworkType.BTC_MAINNET,
                                            tx,
                                            output.getValue(),
                                            CryptoCurrency.BTC,
                                            true
                                    ));

                            log.warn("\u001B[32m" + "|{}| {} BTC RECEIVED !" + "\u001B[0m",
                                    exchangeDetails.getBtcAddress(),
                                    output.getValue());
                        }
                    }
                });
    }
}
