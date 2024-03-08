const scanQr = (videoEl, callback) => {
  videoEl.style.display = "block"
  videoEl.scrollIntoView()

  const scanner = new QrScanner(
    videoEl,
    result => {
      videoEl.style.display = "none"
      scanner.stop()
      callback(result.data)
    }, {
      highlightScanRegion: true
    }
  )

  scanner.start()
  return scanner
}

const scanDriverQr = (input, videoId, carrierEoriId, driverIdDigitsId, licensePlaceId) => {
  const videoEl = document.getElementById(videoId)

  if (input.scanner) {
    input.scanner.stop()
    input.scanner = null
    videoEl.style.display = "none"
    return
  }

  input.scanner = scanQr(videoEl, (data) => {
    console.log(data)
    const m = data.match("^:dil-demo:([^:]+):([^:]+):([^:]+)")
    if (m) {
      const [_, eori, driver, plate] = m
      document.getElementById(carrierEoriId).value = eori
      document.getElementById(driverIdDigitsId).value = driver
      document.getElementById(licensePlaceId).value = plate
    } else {
      alert("QR code niet herkend; " + data);
    }
  })
}
