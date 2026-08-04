# Hands-On Guide — Phase 9 Part 3: HMAC Signature & Delivery

## Goal
- SignatureGenerator (HMAC-SHA256) fully explained
- WebhookDispatcher (HTTP POST with signature headers)
- How merchant verifies authenticity
- Git commit

---

## How HMAC Signing Works

```
WHY SIGN WEBHOOKS?
Without signing, anyone could POST fake events to merchant's URL:
  Hacker → POST to merchant.com/webhooks → { "payment.captured", amount: ₹99999 }
  Merchant trusts it → ships product → loses money! 💀

WITH HMAC SIGNING:
  PayFlow → signs payload with merchant's SECRET → sends with signature header
  Merchant → verifies signature using SAME secret → knows it's really from PayFlow
  Hacker doesn't know the secret → can't forge valid signature ✓

ALGORITHM: HMAC-SHA256
  Input: secret_key + message
  Output: 64-char hex string (deterministic — same input = same output)
  Property: Impossible to compute without knowing the secret
```

---

## Step 3.1: SignatureGenerator Code (Already Created)

```java
public String sign(String payload, long timestamp, String secret) {
    // Signed content includes timestamp to prevent replay attacks:
    // Even if attacker captures a real webhook, they can't replay it
    // because timestamp would be old (merchant checks timestamp freshness)
    String signedContent = timestamp + "." + payload;
    
    Mac mac = Mac.getInstance("HmacSHA256");
    SecretKeySpec keySpec = new SecretKeySpec(
        secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    mac.init(keySpec);
    byte[] hash = mac.doFinal(signedContent.getBytes(StandardCharsets.UTF_8));
    return "sha256=" + HexFormat.of().formatHex(hash);
}
```

---

## Step 3.2: How Merchant Verifies (Their Code)

```java
// Merchant's server receives webhook:
String body = request.getBody();
String signature = request.getHeader("X-PayFlow-Signature"); // "sha256=abc123..."
String timestamp = request.getHeader("X-PayFlow-Timestamp"); // "1721484000"

// Step 1: Check timestamp (reject if >5 minutes old — prevents replay)
long eventTime = Long.parseLong(timestamp);
long now = Instant.now().getEpochSecond();
if (Math.abs(now - eventTime) > 300) { // 5 minutes
    return ResponseEntity.status(401).body("Timestamp too old");
}

// Step 2: Compute HMAC using their stored webhook_secret
String content = timestamp + "." + body;
String computed = "sha256=" + hmacSha256(webhookSecret, content);

// Step 3: Compare
if (computed.equals(signature)) {
    // ✅ Valid — process the event
    processEvent(body);
    return ResponseEntity.ok().build();
} else {
    // ❌ Forged — reject
    return ResponseEntity.status(401).body("Invalid signature");
}
```

---

## Git Commit

```cmd
git commit -m "Phase 9 Part 3: HMAC signature generation + webhook delivery logic"
```

## Next Step → Phase 9 Part 4
