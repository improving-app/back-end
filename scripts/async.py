import logging
import uuid
from grpc import aio
import asyncio
from asyncio import gather
from aiomultiprocess import Pool, Worker
import time

import product_availability_pb2
import product_availability_pb2_grpc

logging.basicConfig(filename='process.log', level=logging.INFO)

ENTITY_COUNT = 100
ITEMS_PER_ENTITY = 100
COUNTER_OUTPUT_GRANULARITY = 1000
PROCESSES = 10
ON_ERROR_SLEEP_SECONDS = 10
SERVER_IP = '34.86.125.164:80' #sub with real ip

channel = aio.insecure_channel(SERVER_IP)
stub = product_availability_pb2_grpc.ProductAvailabilityServiceStub(channel)

async def process(name):
    counter = 1
    successes = 0
    failures = 0
    logging.info("starting process:" + str(name))

    async def call(uniqueStyle):
        await stub.AddItem(product_availability_pb2.AddItemRequest(style=uniqueStyle, color='red', size='10'))

    for entity in range(0, ENTITY_COUNT):
        uniqueStyle = str(uuid.uuid4().hex)

        try:
            for item in range(0, ITEMS_PER_ENTITY):
                await call(uniqueStyle)
                successes += 1

        except Exception as ex:
            try:
                logging.warning("retrying for style:" + uniqueStyle)
                time.sleep(ON_ERROR_SLEEP_SECONDS)
                await call(uniqueStyle) # do a single retry
                successes += 1
            except:
                failures += 1
                logging.error("failure retrying for style:" + uniqueStyle)
        finally:
            logging.info("process:" + str(name) + " completed with " + str(successes) + " successful calls" + " and " + str(failures) + " failures")

async def main():
    for p in range(0, PROCESSES):
        await process('process ' + str(p + 1))

if __name__ == '__main__':
    start_time = time.time()
    loop = asyncio.get_event_loop()
    loop.run_until_complete(main())
    end_time = time.time()
    out = "total elapsed seconds: " + str(end_time-start_time)
    logging.info(out)
    print(out)
