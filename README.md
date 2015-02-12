# Coral

A modular framework for building applications using Reef [Reef](https://github.com/gec/reef) (an open source smart grid platform).

##Technologies

- Client -- HTML 5 [AngularJS](http://angularjs.org)
- Server -- [Play](http://www.playframework.com) framework using [Scala](http://www.scala-lang.org)

## Projects using Coral

- [ReefGUI](https://github.com/gec/reefgui) Administrative console for Reef

## Installing from Source

1.  Install [Reef](https://github.com/gec/reef) or use an existing Reef server.
1.  Install [Play Framework](http://www.playframework.com/download) 2.3.6
1.  Clone the repository: `git clone https://github.com/gec/coral.git`
1.  > cd coral
1.  Edit `reef.cfg` so AMQP host and credentials refer to your Reef server (i.e. properties starting with `org.totalgrid.reef.amqp`).

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

[Apache 2.0](http://www.apache.org/licenses/LICENSE-2.0)

