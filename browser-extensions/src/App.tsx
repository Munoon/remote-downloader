import {useEffect, useState} from "react";
import {DefaultPageLayout} from "@/ui/layouts/DefaultPageLayout";
import Downloads from "./Downloads";
import WebSocketClient, {buildOnWebSocketClosedHandler, buildOnWebSocketErrorHandler} from "./api/client";
import {ConnectionContext, UserCredentialsContext} from "./context";
import browserClient, {UserCredentials} from "./browserClient.tsx";
import LoginCard from "./LoginCard";

export default function App() {
  const [connection, setConnection] = useState<{
    connected: boolean
    connecting: boolean
    failedToConnectReason?: string
    client?: WebSocketClient,
  }>({ connected: false, connecting: false });
  const [credentials, setCredentials] = useState<UserCredentials | undefined>();
  
  async function effect() {
    const credentials = await browserClient.getCredentials();
    if (credentials) {
      setCredentials(credentials);
      const client = new WebSocketClient(credentials, {
        onOpen() {
          setConnection({
            connected: true,
            connecting: false,
            client
          });
        },
        onClose: buildOnWebSocketClosedHandler(setConnection),
        onError: buildOnWebSocketErrorHandler(setConnection)
      });

      setConnection({
        connected: false,
        connecting: true,
        client
      });
    }
  }
  useEffect(() => {
    effect()
  }, []);

  return (
    <ConnectionContext.Provider value={{ ...connection, setConnection }}>
      <UserCredentialsContext.Provider value={{ credentials, setCredentials }}>
        <main className="min-w-max min-h-max">
          <DefaultPageLayout>
            <div className="flex w-144 flex-col items-start gap-3 bg-default-background px-3 py-3">
              <Downloads />
              {!credentials && <LoginCard />}
            </div>
          </DefaultPageLayout>
        </main>
      </UserCredentialsContext.Provider>
    </ConnectionContext.Provider>
  )
}