package com.ledger.exchangerate;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/exchange-rates")
@Tag(name = "Exchange Rates", description = "View current exchange rates")
public class ExchangeRateController {

    private final ExchangeRateRepository exchangeRateRepository;

    public ExchangeRateController(ExchangeRateRepository exchangeRateRepository) {
        this.exchangeRateRepository = exchangeRateRepository;
    }

    @GetMapping
    @Operation(
        summary = "Get all exchange rates",
        description = "Returns all configured exchange rates. Rates can be modified via debug endpoints.",
        responses = {
            @ApiResponse(responseCode = "200", description = "List of exchange rates")
        }
    )
    public ResponseEntity<List<Map<String, Object>>> getAllRates() {
        List<Map<String, Object>> rates = exchangeRateRepository.findAll().stream()
                .map(er -> Map.<String, Object>of(
                        "id", er.getId(),
                        "sourceCurrency", er.getSourceCurrency(),
                        "targetCurrency", er.getTargetCurrency(),
                        "rate", er.getRate().toPlainString(),
                        "updatedAt", er.getUpdatedAt().toString()
                ))
                .toList();
        return ResponseEntity.ok(rates);
    }
}
