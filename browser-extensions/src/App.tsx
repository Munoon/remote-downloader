import { useEffect, useState } from "react";
import Downloads from "./Downloads";
import client from "./api/client";

export default function App() {
  const [connected, setConnected] = useState(false);
  useEffect(() => {
    client.getServerHello()
      .then(serverHello => {
        setConnected(true);
      })
  }, [])

  return (
    <main className="min-w-max min-h-max">
      {connected && <Downloads />}
    </main>
  )
}
