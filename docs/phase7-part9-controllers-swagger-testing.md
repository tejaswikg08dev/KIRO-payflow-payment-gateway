# Hands-On Guide — Phase 7 Part 9: Controllers, Swagger & Testing

## Goal

- Swagger UI accessible at http://localhost:8084/swagger-ui.html
- POST /internal/route documented with request/response schemas
- Complete Postman collection entry for routing tests
- Phase 7 FULLY COMPLETE

---

## Swagger UI

Open: **http://localhost:8084/swagger-ui.html**

Shows:
```
Routing (Internal)
  POST /internal/route   Route a payment to the best bank
```

Click "Try it out" → paste route request JSON → see bank response.

---

## Postman Entry

```
📁 PayFlow Routing Service (Internal)
└── POST Route Payment
    URL: http://localhost:8084/internal/route
    Body:
    {
      "paymentId": "pay_test001",
      "cardNumber": "4111111111111111",
      "cardExpiry": "2812",
      "cardLast4": "1111",
      "amount": 500000,
      "currency": "INR",
      "merchantId": "merch_test"
    }
```

---

## Summary of All Test Cards

| Card Number | Expected Behavior | Response Code |
|-------------|------------------|:---:|
| 4111111111111111 | ✅ APPROVE | 00 |
| 4000000000000002 | ❌ Decline (insufficient funds) | 51 |
| 4000000000000069 | ❌ Decline (expired) | 54 |
| 4000000000000077 | ⏱️ TIMEOUT (no response) | — |
| 4000000000000036 | ❌ Decline (lost card) | 41 |
| 5500000000000004 | ✅ APPROVE (Mastercard) | 00 |
| 6521000000000005 | ✅ APPROVE (RuPay) | 00 |
| Any other | 🎲 90% approve, 10% decline | 00/05 |

---

## Interview Notes

**Q: "Explain your ISO 8583 implementation"**
> "I built a custom encoder/decoder that converts Java objects to binary bytes following the ISO 8583 standard. The message has an MTI (4 bytes identifying message type), a bitmap (64 bits indicating present fields), and data fields in order. I encode FIXED fields with padding and LLVAR fields with 2-digit length prefixes. Messages travel over TCP with a 2-byte length header for framing. The bank simulator on the other end parses these messages and responds with approve/decline codes."

**Q: "Why not use a library like jPOS?"**
> "I built it custom to deeply understand the protocol — every byte, every bit of the bitmap. For interviews, I can explain the wire format in detail. In production, I'd use jPOS for its robustness, testing, and multi-threaded connection management. But understanding the fundamentals is essential before using any library."

---

## Next Step → Phase 8: Settlement Service
