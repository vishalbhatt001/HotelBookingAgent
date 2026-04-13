package com.enterprise.booking.api;

import com.enterprise.booking.service.EnterpriseBookingFlowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/booking")
@Validated
@Tag(name = "Booking Agent", description = "Two-step booking flow endpoints")
public class BookingAgentController {

    private final EnterpriseBookingFlowService flowService;

    public BookingAgentController(EnterpriseBookingFlowService flowService) {
        this.flowService = flowService;
    }

    @PostMapping("/turn")
    @Operation(
            summary = "Process one chat turn",
            description = "Handles one user turn in the booking flow: collect data, preview price, and confirm.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Turn processed successfully",
                            content = @Content(schema = @Schema(implementation = BookingTurnResponse.class))
                    )
            }
    )
    public BookingTurnResponse handleTurn(@RequestBody BookingTurnHttpRequest request) {
        BookingTurnRequest turn = new BookingTurnRequest(
                request.sessionId(),
                request.userMessage(),
                request.hotelId(),
                request.checkin(),
                request.checkout(),
                request.adultCount()
        );
        return flowService.handleTurn(turn);
    }

    public record BookingTurnHttpRequest(
            String sessionId,
            @NotBlank String userMessage,
            String hotelId,
            String checkin,
            String checkout,
            Integer adultCount
    ) {
    }
}
