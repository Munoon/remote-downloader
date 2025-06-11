import { useEffect, useState } from "react";
import { DefaultPageLayout } from "@/ui/layouts/DefaultPageLayout";
import Downloads from "./Downloads";
import client from "./api/client";
import { ConnectionContext, ConnectionContextType } from "./context";

export default function App() {
  const [connection, setConnection] = useState<ConnectionContextType>({ authenticated: true, connected: false, connecting: true });
  useEffect(() => {
    client.getServerHello()
      .then(serverHello => {
        setConnection({ authenticated: true, connected: true, connecting: false });
      })
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
