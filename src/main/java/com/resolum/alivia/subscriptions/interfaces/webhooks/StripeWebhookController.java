package com.resolum.alivia.subscriptions.interfaces.webhooks;

import com.resolum.alivia.subscriptions.domain.model.commands.ReceiveStripeWebhookCommand;
import com.resolum.alivia.subscriptions.domain.services.StripeWebhookEventHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Webhooks de Stripe", description = "Recepción firmada de eventos de Stripe")
public class StripeWebhookController {
    private final StripeWebhookEventHandler eventHandler;

    public StripeWebhookController(StripeWebhookEventHandler eventHandler) {
        this.eventHandler = eventHandler;
    }

    @PostMapping("/api/v1/webhooks/stripe")
    @Operation(summary = "Recibir un evento firmado de Stripe",
            description = "Verifica la firma y guarda el evento una sola vez para su posterior procesamiento.")
    @ApiResponse(responseCode = "200", description = "Evento recibido o ya registrado")
    @ApiResponse(responseCode = "400", description = "Firma o evento inválido")
    @ApiResponse(responseCode = "503", description = "Secreto de firma no configurado")
    public ResponseEntity<Void> receive(@RequestBody String payload,
                                        @RequestHeader("Stripe-Signature") String signature) {
        eventHandler.handle(new ReceiveStripeWebhookCommand(payload, signature));
        return ResponseEntity.ok().build();
    }
}
