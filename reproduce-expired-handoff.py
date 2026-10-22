"""Force the joined fixture's late-connection ordering using real loopback sockets.

This is a separate protocol experiment. No gate source or timeout is changed.
The accept operation must have failed before the client connects; its small
timeout only reaches that exact ordering, without depending on machine speed.
"""
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
import json, socket, unittest

check = unittest.TestCase()
results = []
for iteration in range(1, 11):
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as listener:
        listener.bind(('127.0.0.1', 0))
        listener.listen(1)
        listener.settimeout(0.001)
        with ThreadPoolExecutor(max_workers=1) as receiver:
            accepted = receiver.submit(listener.accept)
            with check.assertRaises(TimeoutError):
                accepted.result(timeout=5)
            check.assertTrue(accepted.done())
            check.assertIsInstance(accepted.exception(), TimeoutError)
            check.assertNotEqual(listener.fileno(), -1)
            with socket.create_connection(listener.getsockname(), timeout=5) as client:
                client.settimeout(0.1)
                with check.assertRaises(TimeoutError):
                    client.recv(1)
                results.append({'iteration': iteration, 'acceptFutureFailedBeforeConnect': True,
                                'listenerStillOpen': True, 'lateConnectSucceeded': True,
                                'replyUnavailable': True, 'attempts': 1})
destination = Path(__import__('sys').argv[1])
destination.write_text(json.dumps({'experiment': 'expired accept followed by late connection',
                                 'iterations': results, 'confirmed': len(results)}, indent=2) + '\n')
