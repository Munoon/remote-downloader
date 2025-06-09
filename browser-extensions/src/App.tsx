import { useEffect, useState } from "react";
import { DefaultPageLayout } from "@/ui/layouts/DefaultPageLayout";
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
      <DefaultPageLayout>
        <div className="flex w-144 flex-col items-start gap-3 bg-default-background px-3 py-3">
          {connected && <Downloads />}
        </div>
      </DefaultPageLayout>
    </main>
  )
}
