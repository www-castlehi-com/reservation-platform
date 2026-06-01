package com.stay.reservation.bookingpayment.booking.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.stay.reservation.bookingpayment.booking.domain.BookingStatus;
import com.stay.reservation.bookingpayment.booking.dto.BookingRequest;
import com.stay.reservation.bookingpayment.booking.dto.BookingResponse;
import com.stay.reservation.bookingpayment.payment.model.PaymentType;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

	@Mock
	private BookingService bookingService;

	@InjectMocks
	private BookingFacade bookingFacade;

	private BookingRequest validRequest;
	private BookingResponse mockResponse;

	@BeforeEach
	void setUp() {
		validRequest = new BookingRequest(
			1L,
			new BookingRequest.Payment(
				159000L,
				List.of(new BookingRequest.Payment.Method(PaymentType.Y_POINT, 159000L, null, null))
			),
			"홍길동",
			"010-1234-5678"
		);
		mockResponse = new BookingResponse(1234L, "B20261224-99999", BookingStatus.CONFIRMED, 159000L, java.time.LocalDateTime.now());
	}

	@Test
	@DisplayName("정상적인 최초 예약 생성 흐름 Facade 위임 테스트")
	void success_bookingCreated() {
		when(bookingService.createBooking(validRequest, 1001L, "key-123")).thenReturn(mockResponse);

		BookingResponse response = bookingFacade.createBooking(1001L, "key-123", validRequest);

		assertNotNull(response);
		assertEquals("B20261224-99999", response.bookingNumber());
		assertEquals(BookingStatus.CONFIRMED, response.status());

		verify(bookingService).createBooking(validRequest, 1001L, "key-123");
	}
}

