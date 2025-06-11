import { useEffect, useState } from "react";
import { DefaultPageLayout } from "@/ui/layouts/DefaultPageLayout";
import Downloads from "./Downloads";
import WebSocketClient from "./api/client";
import { ConnectionContext, ConnectionContextType } from "./context";

export default function App() {
  const [connection, setConnection] = useState<ConnectionContextType>({ authenticated: true, connected: false, connecting: false });
  
  useEffect(() => {
    const client = new WebSocketClient('ws://127.0.0.1:8080/websocket', {
      onOpen() {
        setConnection({
          authenticated: true,
          connected: true,
          connecting: false,
          client
        })
      },
      onClose() {
        setConnection({
          authenticated: true,
          connected: false,
          connecting: false,
          failedToConnectReason: 'Connection closed.',
          client
        })
      }
    })

    setConnection({
      authenticated: true,
      connected: false,
      connecting: false,
      client
    });
  }, [])

  return (
    <ConnectionContext.Provider value={connection}>
      <main className="min-w-max min-h-max">
        <DefaultPageLayout>
          <div className="flex w-144 flex-col items-start gap-3 bg-default-background px-3 py-3">
            <Downloads />
          </div>
        </DefaultPageLayout>
      </main>
    </ConnectionContext.Provider>
  )
}
