# greenbus-web-core

Core web services for building applications using GreenBus [Reef](https://github.com/gec/greenbus) (an open source smart grid platform).

## Services
* REST
* WebSocket

##Technologies

- Client -- HTML 5 [AngularJS](http://angularjs.org)
- Server -- [Play](http://www.playframework.com) framework using [Scala](http://www.scala-lang.org)

## Projects using green-bus-web-core

- [greenbus-web-hmi](https://github.com/gec/greenbus-web-hmi) Microgrid controller for GreenBus

## Installing from Source

1.  Install [GreenBus](https://github.com/gec/greenbus) or use an existing GreenBus server.
1.  Install [Play Framework](http://www.playframework.com/download) 2.3.6
1.  Clone the repository: `git clone https://github.com/gec/greenbus-web-core.git`
1.  > cd greenbus-web-core
1.  Edit `greenbus.cfg` so AMQP host and credentials refer to your GreenBus server (i.e. properties starting with `org.greenbus.msg.amqp`).

### Running Sample Application

```
> activator "project sample" run
Point your web browser at `http://localhost:9000`
```

### Running Tests

```
> activator "project sample" test
```

## License

This software is under the [Apache 2.0 License](http://www.apache.org/licenses/LICENSE-2.0.html).

