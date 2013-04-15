# REEF GUI

An HTML 5 client for [Reef](https://github.com/gec/reef) (an open source smart grid platform).

## Installing from Source

1.  Install [Reef](https://github.com/gec/reef) or use an existing Reef server.
1.  Install [Play Framework](http://www.playframework.com/download) 2.0.4
1.  Clone the repository: `git clone https://github.com/gec/reefgui.git`
1.  > cd reefgui
1.  Edit `reef.cfg` so AMQP host and credentials refer to your Reef server (i.e. properties starting with `org.totalgrid.reef.amqp`).
1.  > play run
1.  Point your web browser at `http://localhost:9000`

## Screenshot

![Reef GUI Screenshot](https://github.com/gec/reefgui/raw/master/screenshot.png)

## License

[Apache 2.0](http://www.apache.org/licenses/LICENSE-2.0)

